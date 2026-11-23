package com.almaraama.identity.application;

import com.almaraama.identity.api.AccountStatus;
import com.almaraama.identity.api.events.CredentialLockedV1;
import com.almaraama.identity.api.events.LoginFailedV1;
import com.almaraama.identity.api.events.LoginSucceededV1;
import com.almaraama.identity.api.events.RefreshTokenReuseDetectedV1;
import com.almaraama.identity.domain.Credential;
import com.almaraama.identity.domain.CredentialRepository;
import com.almaraama.identity.domain.DeviceSessionRepository;
import com.almaraama.identity.domain.EmailAddress;
import com.almaraama.identity.domain.PasswordHasher;
import com.almaraama.identity.domain.PasswordPolicy;
import com.almaraama.identity.domain.Person;
import com.almaraama.identity.domain.PersonRepository;
import com.almaraama.identity.domain.RefreshToken;
import com.almaraama.identity.domain.SecretTokens;
import com.almaraama.identity.domain.TrustedDevice;
import com.almaraama.shared.logging.LogMasking;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sign-in by e-mail and password, session renewal and sign-out (D5, ADR-030).
 * <p>
 * Failures are returned as outcomes, never thrown: the transaction must commit the failure counter, the lockout
 * and the device revocation, otherwise the protection would be rolled back with the error.
 */
@Service
public class Authentication {

    private static final Logger LOG = LoggerFactory.getLogger(Authentication.class);

    private final PersonRepository persons;
    private final CredentialRepository credentials;
    private final DeviceSessionRepository sessions;
    private final PasswordHasher passwordHasher;
    private final SessionOpener sessionOpener;
    private final UnknownEmailThrottle unknownEmails;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the ports and collaborators of sign-in, renewal and sign-out. */
    Authentication(PersonRepository persons, CredentialRepository credentials, DeviceSessionRepository sessions,
                   PasswordHasher passwordHasher, SessionOpener sessionOpener, UnknownEmailThrottle unknownEmails,
                   ApplicationEventPublisher events, Clock clock) {
        this.persons = persons;
        this.credentials = credentials;
        this.sessions = sessions;
        this.passwordHasher = passwordHasher;
        this.sessionOpener = sessionOpener;
        this.unknownEmails = unknownEmails;
        this.events = events;
        this.clock = clock;
    }

    /** Result of a sign-in, returned rather than thrown so that the failure counter is committed. */
    public sealed interface LoginOutcome {
        /** Signed in: the new session. */
        record Success(OpenedSession session) implements LoginOutcome {
        }

        /** Wrong e-mail or password; the same answer for an unknown, invited or deactivated account. */
        record InvalidCredentials() implements LoginOutcome {
        }

        /** Correct password, but the e-mail is not verified yet (only said after a correct password). */
        record EmailNotVerified() implements LoginOutcome {
        }

        /** Too many failures: wait before trying again. */
        record Locked(Duration retryAfter) implements LoginOutcome {
        }
    }

    /** Result of a session renewal, returned rather than thrown so that a revocation is committed. */
    public sealed interface RenewalOutcome {
        /** The refresh token was valid: a new pair of tokens. */
        record Renewed(OpenedSession session) implements RenewalOutcome {
        }

        /** No valid session on this device. */
        record Invalid() implements RenewalOutcome {
        }
    }

    /**
     * Signs in with e-mail and password (D5): counts failures, locks progressively, and gives unknown e-mails and
     * accounts that cannot sign in the same answer and timing. {@code EmailNotVerified} is returned only after a
     * correct password, so it reveals nothing.
     */
    @Transactional
    public LoginOutcome login(String rawEmail, String rawPassword, String deviceName) {
        Instant now = clock.instant();
        String password = PasswordPolicy.normalize(rawPassword);
        Optional<EmailAddress> email = EmailAddress.tryParse(rawEmail);
        Optional<Person> person = email.flatMap(persons::findByEmail)
                .filter(p -> p.isActive() || p.status() == AccountStatus.PENDING_VERIFICATION);
        Optional<Credential> credential = person.flatMap(p -> credentials.findByPersonId(p.id()));

        if (credential.isEmpty()) {
            return unknownAccount(email.map(EmailAddress::value).orElse(String.valueOf(rawEmail)), password, now);
        }

        Credential current = credential.get();
        if (current.isLocked(now)) {
            LOG.debug("Sign-in refused for person {}: locked", current.personId().value());
            return new LoginOutcome.Locked(Duration.between(now, current.lockedUntil().orElseThrow()));
        }
        boolean matches = current.passwordHash().map(hash -> passwordHasher.matches(password, hash))
                .orElseGet(() -> {
                    passwordHasher.matchAgainstDecoy(password);
                    return false;
                });
        if (!matches) {
            Optional<Instant> lockedUntil = current.recordFailure(now);
            credentials.save(current);
            events.publishEvent(new LoginFailedV1(Optional.of(current.personId()), now));
            LOG.debug("Sign-in failed for person {} ({} consecutive failures)", current.personId().value(),
                    current.failedAttempts());
            if (lockedUntil.isPresent()) {
                events.publishEvent(new CredentialLockedV1(
                        current.personId(), current.failedAttempts(), lockedUntil.get(), now));
                LOG.warn("Sign-in locked for person {} until {}", current.personId().value(), lockedUntil.get());
                return new LoginOutcome.Locked(Duration.between(now, lockedUntil.get()));
            }
            return new LoginOutcome.InvalidCredentials();
        }
        if (!person.orElseThrow().isActive()) {
            LOG.debug("Sign-in of person {} refused: e-mail not verified", current.personId().value());
            return new LoginOutcome.EmailNotVerified();
        }

        current.recordSuccess(now);
        credentials.save(current);
        TrustedDevice device = sessionOpener.registerDevice(current.personId(), deviceName, now);
        events.publishEvent(new LoginSucceededV1(current.personId(), device.id(), now));
        LOG.debug("Person {} signed in on device {}", current.personId().value(), device.id());
        return new LoginOutcome.Success(sessionOpener.issueTokens(device, now));
    }

    /**
     * Replaces a valid refresh token with a new one; a token presented twice ends the device's session (theft
     * detection).
     */
    @Transactional
    public RenewalOutcome renew(String refreshToken) {
        Instant now = clock.instant();
        if (refreshToken == null || refreshToken.isBlank()) {
            return new RenewalOutcome.Invalid();
        }
        Optional<RefreshToken> found = sessions.findRefreshToken(SecretTokens.hash(refreshToken));
        Optional<TrustedDevice> device = found.flatMap(token -> sessions.findDevice(token.deviceId()));
        if (found.isEmpty() || device.isEmpty() || device.get().isRevoked()) {
            return new RenewalOutcome.Invalid();
        }

        RefreshToken token = found.get();
        TrustedDevice trusted = device.get();
        if (token.isUsed()) {
            trusted.revoke(now);
            sessions.save(trusted);
            events.publishEvent(new RefreshTokenReuseDetectedV1(trusted.personId(), trusted.id(), now));
            LOG.warn("Refresh token reused on device {}: session ended", trusted.id());
            return new RenewalOutcome.Invalid();
        }
        boolean personActive = persons.findById(trusted.personId()).map(Person::isActive).orElse(false);
        if (token.isExpired(now) || !personActive) {
            return new RenewalOutcome.Invalid();
        }

        token.markUsed(now);
        sessions.save(token);
        trusted.seen(now);
        sessions.save(trusted);
        return new RenewalOutcome.Renewed(sessionOpener.issueTokens(trusted, now));
    }

    /** Ends the session on the device that holds this refresh token. Unknown tokens are ignored. */
    @Transactional
    public void logout(String refreshToken) {
        if (refreshToken == null || refreshToken.isBlank()) {
            return;
        }
        Instant now = clock.instant();
        sessions.findRefreshToken(SecretTokens.hash(refreshToken))
                .flatMap(token -> sessions.findDevice(token.deviceId()))
                .ifPresent(device -> {
                    device.revoke(now);
                    sessions.save(device);
                });
    }

    /**
     * Treats an e-mail without an account that can sign in like a wrong password: decoy check, same lockout, nothing
     * revealed.
     */
    private LoginOutcome unknownAccount(String emailKey, String password, Instant now) {
        passwordHasher.matchAgainstDecoy(password);
        LOG.debug("Sign-in failed for an e-mail without usable account ({})", LogMasking.email(emailKey));
        Optional<Instant> alreadyLocked = unknownEmails.lockedUntil(emailKey, now);
        if (alreadyLocked.isPresent()) {
            return new LoginOutcome.Locked(Duration.between(now, alreadyLocked.get()));
        }
        events.publishEvent(new LoginFailedV1(Optional.empty(), now));
        return unknownEmails.recordFailure(emailKey, now)
                .<LoginOutcome>map(until -> new LoginOutcome.Locked(Duration.between(now, until)))
                .orElseGet(LoginOutcome.InvalidCredentials::new);
    }
}
