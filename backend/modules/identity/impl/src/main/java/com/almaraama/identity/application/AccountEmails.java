package com.almaraama.identity.application;

import com.almaraama.identity.api.events.AccountEmailKind;
import com.almaraama.identity.api.events.AccountEmailRequestedV1;
import com.almaraama.identity.domain.Person;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.logging.LogMasking;
import java.time.Instant;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Requests an account e-mail inside the caller's transaction (ADR-031): only an event is written to the outbox, so
 * the e-mail leaves after the commit and is retried if the relay is down. No link is created here: the
 * {@link AccountEmailDispatcher} creates it when it sends, so its secret is never stored anywhere.
 */
@Component
class AccountEmails {

    private static final Logger LOG = LoggerFactory.getLogger(AccountEmails.class);

    private final ApplicationEventPublisher events;
    private final IdentityProperties properties;

    /**
     * @param events     writes {@code AccountEmailRequestedV1} to the outbox (CF-6)
     * @param properties link lifetimes
     */
    AccountEmails(ApplicationEventPublisher events, IdentityProperties properties) {
        this.events = events;
        this.properties = properties;
    }

    /** Requests an e-mail of this kind for the person; returns when its link will expire (now for no link). */
    Instant request(Person person, AccountEmailKind kind, Actor requestedBy, Instant now) {
        events.publishEvent(new AccountEmailRequestedV1(person.id(), kind, requestedBy, now));
        LOG.debug("Account e-mail {} requested for person {} (***@{}) by {}", kind, person.id().value(),
                person.email().domain(), requestedBy.type());
        return now.plus(properties.linkTtl(kind));
    }

    /** Masks an e-mail for a log line, for callers that only have the typed value. */
    static String masked(String email) {
        return LogMasking.email(email);
    }
}
