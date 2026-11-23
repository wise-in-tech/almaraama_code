package com.almaraama.identity.application;

import com.almaraama.identity.api.events.AccountEmailKind;
import com.almaraama.identity.api.AccountStatus;
import com.almaraama.identity.api.events.EmailVerifiedV1;
import com.almaraama.identity.api.events.PersonActivatedV1;
import com.almaraama.identity.domain.EmailAddress;
import com.almaraama.identity.domain.Person;
import com.almaraama.identity.domain.PersonRepository;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.logging.LogMasking;
import java.time.Clock;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** E-mail verification (D1, ADR-030): use the link, or ask for a new one without revealing anything. */
@Service
public class EmailVerification {

    private static final Logger LOG = LoggerFactory.getLogger(EmailVerification.class);

    private final PersonRepository persons;
    private final AccountLinks links;
    private final AccountEmails emails;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the ports and collaborators of the e-mail verification. */
    EmailVerification(PersonRepository persons, AccountLinks links, AccountEmails emails,
                      ApplicationEventPublisher events, Clock clock) {
        this.persons = persons;
        this.links = links;
        this.emails = emails;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Uses a verification link: the e-mail is verified and the account waiting for it becomes active ({@code
     * EmailVerifiedV1}, {@code PersonActivatedV1}). {@code LINK_INVALID_OR_EXPIRED} for an unusable link.
     */
    @Transactional
    public void verify(String token) {
        Instant now = clock.instant();
        AccountLinks.Opened opened = links.open(token, AccountEmailKind.EMAIL_VERIFICATION,
                p -> p.status() == AccountStatus.PENDING_VERIFICATION, now);
        links.use(opened, now);
        Person person = opened.person();
        boolean activated = person.verifyEmail(now);
        persons.save(person);
        events.publishEvent(new EmailVerifiedV1(person.id(), now));
        if (activated) {
            events.publishEvent(new PersonActivatedV1(person.id(), now));
        }
        LOG.debug("Person {} verified their e-mail", person.id().value());
    }

    /** Sends a new verification link when an account with this e-mail waits for it; silent in every case. */
    @Transactional
    public void resend(String rawEmail) {
        Instant now = clock.instant();
        EmailAddress.tryParse(rawEmail).flatMap(persons::findByEmail)
                .filter(p -> p.status() == AccountStatus.PENDING_VERIFICATION)
                .ifPresentOrElse(
                        p -> emails.request(p, AccountEmailKind.EMAIL_VERIFICATION, Actor.platform(), now),
                        () -> LOG.debug("Verification resend for {}: nothing to send", LogMasking.email(rawEmail)));
    }
}
