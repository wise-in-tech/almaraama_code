package com.almaraama.identity.application;

import com.almaraama.identity.api.events.AccountEmailKind;
import com.almaraama.identity.api.AccountStatus;
import com.almaraama.identity.api.events.EmailVerifiedV1;
import com.almaraama.identity.api.events.PasswordChangedV1;
import com.almaraama.identity.domain.Credential;
import com.almaraama.identity.domain.CredentialRepository;
import com.almaraama.identity.domain.EmailAddress;
import com.almaraama.identity.domain.PasswordHasher;
import com.almaraama.identity.domain.PasswordPolicy;
import com.almaraama.identity.domain.Person;
import com.almaraama.identity.domain.PersonRepository;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.logging.LogMasking;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Forgotten password (D5, ADR-030): a reset link by e-mail, then a new password that ends every session. The request
 * never reveals whether an account exists.
 */
@Service
public class PasswordReset {

    private static final Logger LOG = LoggerFactory.getLogger(PasswordReset.class);

    private final PersonRepository persons;
    private final CredentialRepository credentials;
    private final AccountLinks links;
    private final AccountEmails emails;
    private final PasswordPolicy passwordPolicy;
    private final PasswordHasher passwordHasher;
    private final SessionOpener sessionOpener;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the ports and collaborators of the forgotten-password flow. */
    PasswordReset(PersonRepository persons, CredentialRepository credentials, AccountLinks links,
                  AccountEmails emails, PasswordPolicy passwordPolicy, PasswordHasher passwordHasher,
                  SessionOpener sessionOpener, ApplicationEventPublisher events, Clock clock) {
        this.persons = persons;
        this.credentials = credentials;
        this.links = links;
        this.emails = emails;
        this.passwordPolicy = passwordPolicy;
        this.passwordHasher = passwordHasher;
        this.sessionOpener = sessionOpener;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Requests a link for this e-mail, always silently: an {@code ACTIVE} account gets a reset link, an {@code
     * INVITED} one a new "set my password" link, anything else nothing. The hourly limit applies when sending.
     */
    @Transactional
    public void request(String rawEmail) {
        Instant now = clock.instant();
        Optional<Person> person = EmailAddress.tryParse(rawEmail).flatMap(persons::findByEmail);
        Optional<AccountEmailKind> kind = person.flatMap(p -> switch (p.status()) {
            case ACTIVE -> Optional.of(AccountEmailKind.PASSWORD_RESET);
            case INVITED -> Optional.of(AccountEmailKind.SET_PASSWORD);
            case PENDING_VERIFICATION, DEACTIVATED -> Optional.<AccountEmailKind>empty();
        });
        if (kind.isEmpty()) {
            LOG.debug("Password reset asked for {}: no account can receive it", LogMasking.email(rawEmail));
            return;
        }
        emails.request(person.get(), kind.get(), Actor.platform(), now);
    }

    /**
     * Uses a reset link: checks the password rules ({@code PASSWORD_*}), replaces the password, verifies the e-mail,
     * clears the lock, ends every session of the person and sends a "password changed" e-mail. {@code
     * LINK_INVALID_OR_EXPIRED} for an unusable link.
     */
    @Transactional
    public void confirm(String token, String rawPassword) {
        Instant now = clock.instant();
        AccountLinks.Opened opened = links.open(token, AccountEmailKind.PASSWORD_RESET,
                p -> p.status() == AccountStatus.ACTIVE, now);
        Person person = opened.person();
        String password = passwordPolicy.check(rawPassword, person.email());

        links.use(opened, now);
        String hash = passwordHasher.hash(password);
        credentials.findByPersonId(person.id()).ifPresentOrElse(
                existing -> {
                    existing.changePassword(hash, now);
                    credentials.save(existing);
                },
                () -> credentials.save(Credential.create(person.id(), hash, now)));
        boolean firstVerification = person.emailVerifiedAt().isEmpty();
        person.verifyEmail(now);
        persons.save(person);
        int signedOut = sessionOpener.endAllSessions(person.id(), now);
        if (firstVerification) {
            events.publishEvent(new EmailVerifiedV1(person.id(), now));
        }
        events.publishEvent(new PasswordChangedV1(person.id(), true, now));
        emails.request(person, AccountEmailKind.PASSWORD_CHANGED, Actor.platform(), now);
        LOG.debug("Person {} reset their password; {} device sessions ended", person.id().value(), signedOut);
    }
}
