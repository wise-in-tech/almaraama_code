package com.almaraama.identity.application;

import com.almaraama.identity.api.events.AccountEmailKind;
import com.almaraama.identity.api.AccountStatus;
import com.almaraama.identity.api.events.EmailVerifiedV1;
import com.almaraama.identity.api.events.LoginSucceededV1;
import com.almaraama.identity.api.events.PasswordChangedV1;
import com.almaraama.identity.api.events.PersonActivatedV1;
import com.almaraama.identity.domain.Credential;
import com.almaraama.identity.domain.CredentialRepository;
import com.almaraama.identity.domain.PasswordHasher;
import com.almaraama.identity.domain.PasswordPolicy;
import com.almaraama.identity.domain.Person;
import com.almaraama.identity.domain.PersonRepository;
import com.almaraama.identity.domain.TrustedDevice;
import com.almaraama.shared.i18n.Language;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The "set my password" link of an account created by a manager (D4, ADR-030): greet the person, then let them choose
 * their password, which also verifies their e-mail, and sign them in.
 */
@Service
public class InvitationAcceptance {

    private static final Logger LOG = LoggerFactory.getLogger(InvitationAcceptance.class);

    private final AccountLinks links;
    private final PersonRepository persons;
    private final CredentialRepository credentials;
    private final PasswordPolicy passwordPolicy;
    private final PasswordHasher passwordHasher;
    private final SessionOpener sessionOpener;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the ports and collaborators of the "set my password" flow. */
    InvitationAcceptance(AccountLinks links, PersonRepository persons, CredentialRepository credentials,
                         PasswordPolicy passwordPolicy, PasswordHasher passwordHasher, SessionOpener sessionOpener,
                         ApplicationEventPublisher events, Clock clock) {
        this.links = links;
        this.persons = persons;
        this.credentials = credentials;
        this.passwordPolicy = passwordPolicy;
        this.passwordHasher = passwordHasher;
        this.sessionOpener = sessionOpener;
        this.events = events;
        this.clock = clock;
    }

    /** What the screen shows before the password is chosen: first name, e-mail and language. */
    public record Preview(String firstName, String email, Language language) {
    }

    /** Checks a link before the password is chosen; {@code LINK_INVALID_OR_EXPIRED} for any unusable link. */
    @Transactional(readOnly = true)
    public Preview inspect(String token) {
        Person person = open(token, clock.instant()).person();
        return new Preview(person.firstName(), person.email().value(), person.language());
    }

    /**
     * Uses the link once: checks the password rules ({@code PASSWORD_*}), sets the password, verifies the e-mail,
     * activates the account, opens a session and publishes {@code PersonActivatedV1}, {@code EmailVerifiedV1} and
     * {@code PasswordChangedV1}.
     */
    @Transactional
    public OpenedSession accept(String token, String rawPassword, String deviceName) {
        Instant now = clock.instant();
        AccountLinks.Opened opened = open(token, now);
        Person person = opened.person();
        String password = passwordPolicy.check(rawPassword, person.email());

        links.use(opened, now);
        person.verifyEmail(now);
        persons.save(person);
        String hash = passwordHasher.hash(password);
        credentials.findByPersonId(person.id()).ifPresentOrElse(
                existing -> {
                    existing.changePassword(hash, now);
                    credentials.save(existing);
                },
                () -> credentials.save(Credential.create(person.id(), hash, now)));
        events.publishEvent(new EmailVerifiedV1(person.id(), now));
        events.publishEvent(new PasswordChangedV1(person.id(), false, now));
        events.publishEvent(new PersonActivatedV1(person.id(), now));

        TrustedDevice device = sessionOpener.registerDevice(person.id(), deviceName, now);
        events.publishEvent(new LoginSucceededV1(person.id(), device.id(), now));
        LOG.debug("Person {} set their password and signed in", person.id().value());
        return sessionOpener.issueTokens(device, now);
    }

    /** A usable "set my password" link of an account still waiting for it. */
    private AccountLinks.Opened open(String token, Instant now) {
        return links.open(token, AccountEmailKind.SET_PASSWORD, p -> p.status() == AccountStatus.INVITED, now);
    }
}
