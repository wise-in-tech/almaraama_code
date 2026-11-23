package com.almaraama.identity.domain;

import com.almaraama.identity.api.events.AccountEmailKind;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.UUID;

/**
 * Trace of one account e-mail (ADR-031): who, which kind, when and with what outcome, never the address, the content
 * or the link. Counts towards the hourly sending limit; append-only.
 */
@Entity
@Table(schema = "identity", name = "email_dispatch")
public class EmailDispatch {

    /** What happened to the e-mail. Glossary {@code EmailDispatchOutcome}. */
    public enum Outcome {
        SENT,
        FAILED,
        THROTTLED
    }

    @Id
    private UUID id;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Enumerated(EnumType.STRING)
    @Column(name = "kind", nullable = false)
    private AccountEmailKind kind;

    @Enumerated(EnumType.STRING)
    @Column(name = "outcome", nullable = false)
    private Outcome outcome;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    /** For JPA only. */
    protected EmailDispatch() {
    }

    /** Records an e-mail sent, failed or held back by the limit. */
    public static EmailDispatch record(PersonId personId, AccountEmailKind kind, Outcome outcome, Instant now) {
        EmailDispatch dispatch = new EmailDispatch();
        dispatch.id = UuidV7.next();
        dispatch.personId = personId.value();
        dispatch.kind = kind;
        dispatch.outcome = outcome;
        dispatch.createdAt = now;
        return dispatch;
    }

    /** What happened. */
    public Outcome outcome() {
        return outcome;
    }
}
