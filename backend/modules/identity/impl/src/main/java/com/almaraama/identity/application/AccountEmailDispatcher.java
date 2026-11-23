package com.almaraama.identity.application;

import com.almaraama.identity.api.events.AccountEmailKind;
import com.almaraama.identity.api.AccountStatus;
import com.almaraama.identity.api.events.AccountEmailRequestedV1;
import com.almaraama.identity.api.events.AccountEmailThrottledV1;
import com.almaraama.identity.domain.AccountToken;
import com.almaraama.identity.domain.AccountTokenRepository;
import com.almaraama.identity.domain.EmailDispatch;
import com.almaraama.identity.domain.EmailDispatchRepository;
import com.almaraama.identity.domain.EmailMessage;
import com.almaraama.identity.domain.EmailSender;
import com.almaraama.identity.domain.EmailTemplates;
import com.almaraama.identity.domain.Person;
import com.almaraama.identity.domain.PersonRepository;
import com.almaraama.identity.domain.SecretTokens;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Sends the account e-mails after the request is committed (ADR-031), in its own transaction: creates the
 * single-use link (only its hash is stored, previous links of the same kind are revoked), writes the e-mail in the
 * person's language and hands it to the relay. If sending fails, the transaction is rolled back (the link never
 * existed) and the outbox keeps the request for a retry. At most {@code emailsPerHour} e-mails of one kind per person
 * and hour: beyond, nothing is sent and {@code AccountEmailThrottledV1} is published. The person's row is locked while
 * sending, so concurrent requests for one person are counted one after the other.
 */
@Component
public class AccountEmailDispatcher {

    private static final Logger LOG = LoggerFactory.getLogger(AccountEmailDispatcher.class);

    private final PersonRepository persons;
    private final AccountTokenRepository tokens;
    private final EmailDispatchRepository dispatches;
    private final EmailTemplates templates;
    private final EmailSender sender;
    private final IdentityProperties properties;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the ports used to create the link, write the e-mail and send it. */
    AccountEmailDispatcher(PersonRepository persons, AccountTokenRepository tokens,
                           EmailDispatchRepository dispatches, EmailTemplates templates, EmailSender sender,
                           IdentityProperties properties, ApplicationEventPublisher events, Clock clock) {
        this.persons = persons;
        this.tokens = tokens;
        this.dispatches = dispatches;
        this.templates = templates;
        this.sender = sender;
        this.properties = properties;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Sends the requested e-mail when it still makes sense for the account (for example, no reset link for an account
     * deactivated in the meantime) and the hourly limit allows it. Called after the request is committed, in a new
     * transaction (see {@code AccountEmailListener}); a relay failure throws and rolls everything back.
     */
    @Transactional
    public void dispatch(AccountEmailRequestedV1 request) {
        Instant now = clock.instant();
        Optional<Person> found = persons.lockById(request.personId()).filter(p -> stillRelevant(p, request.kind()));
        if (found.isEmpty()) {
            LOG.debug("Account e-mail {} for person {} no longer relevant: not sent", request.kind(),
                    request.personId().value());
            return;
        }
        Person person = found.get();
        long sent = dispatches.countSentSince(person.id(), request.kind(), now.minus(Duration.ofHours(1)));
        if (sent >= properties.emailsPerHour()) {
            dispatches.save(EmailDispatch.record(person.id(), request.kind(), EmailDispatch.Outcome.THROTTLED, now));
            events.publishEvent(new AccountEmailThrottledV1(person.id(), request.kind(), now));
            LOG.warn("Account e-mail {} for person {} held back: {} sent in the last hour", request.kind(),
                    person.id().value(), sent);
            return;
        }

        Map<String, String> variables = new HashMap<>();
        variables.put("firstName", person.firstName());
        variables.put("appUrl", stripSlash(properties.linkBaseUrl()));
        if (request.kind().carriesLink()) {
            variables.put("link", newLink(person, request, now));
            Duration ttl = properties.linkTtl(request.kind());
            variables.put("validDays", String.valueOf(ttl.toDays()));
            variables.put("validHours", String.valueOf(ttl.toHours()));
        }
        EmailTemplates.Rendered rendered = templates.render(request.kind(), person.language(), variables);
        long started = System.nanoTime();
        sender.send(new EmailMessage(person.email(), rendered.subject(), rendered.text(), rendered.html()));
        dispatches.save(EmailDispatch.record(person.id(), request.kind(), EmailDispatch.Outcome.SENT, now));
        LOG.info("Account e-mail {} sent to person {} (***@{}) in {} ms", request.kind(), person.id().value(),
                person.email().domain(), (System.nanoTime() - started) / 1_000_000);
    }

    /** Whether the account is still in the state this kind of e-mail is meant for. */
    private static boolean stillRelevant(Person person, AccountEmailKind kind) {
        return switch (kind) {
            case SET_PASSWORD -> person.status() == AccountStatus.INVITED;
            case EMAIL_VERIFICATION -> person.status() == AccountStatus.PENDING_VERIFICATION;
            case PASSWORD_RESET -> person.status() == AccountStatus.ACTIVE;
            case PASSWORD_CHANGED, ALREADY_REGISTERED -> !person.isDeactivated();
        };
    }

    /**
     * Revokes the person's open links of this kind and creates a new one; returns the full link with the secret
     * after {@code #}, which the browser never sends to a server (ADR-030).
     */
    private String newLink(Person person, AccountEmailRequestedV1 request, Instant now) {
        tokens.findOpen(person.id(), request.kind()).forEach(previous -> {
            previous.revoke(now);
            tokens.save(previous);
        });
        String secret = SecretTokens.generate();
        tokens.save(AccountToken.issue(person.id(), request.kind(), SecretTokens.hash(secret), request.requestedBy(),
                now, properties.linkTtl(request.kind())));
        return stripSlash(properties.linkBaseUrl()) + path(request.kind()) + "#" + secret;
    }

    /** Path of the application screen that handles each kind of link. */
    private static String path(AccountEmailKind kind) {
        return switch (kind) {
            case SET_PASSWORD -> "/invitation";
            case EMAIL_VERIFICATION -> "/verify-email";
            case PASSWORD_RESET -> "/reset-password";
            case PASSWORD_CHANGED, ALREADY_REGISTERED -> throw new IllegalArgumentException("No link for " + kind);
        };
    }

    /** The base address without its trailing slash. */
    private static String stripSlash(String url) {
        return url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
