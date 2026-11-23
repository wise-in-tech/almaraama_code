package com.almaraama.audit.domain;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.UuidV7;
import java.time.Instant;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * One line of the journal (M2): who did what, to what, when. Written once, never changed. {@code details} holds
 * only identifiers and codes, never secrets nor free personal data.
 */
public record AuditEntry(UUID id, Instant occurredAt, Actor actor, String action, String subjectType,
                         UUID subjectId, Map<String, String> details) {

    public AuditEntry {
        Objects.requireNonNull(id, "id");
        Objects.requireNonNull(occurredAt, "occurredAt");
        Objects.requireNonNull(actor, "actor");
        Objects.requireNonNull(action, "action");
        details = Map.copyOf(details);
    }

    /** A new journal entry with a fresh identifier. */
    public static AuditEntry of(Instant occurredAt, Actor actor, String action, String subjectType, UUID subjectId,
                                Map<String, String> details) {
        return new AuditEntry(UuidV7.next(), occurredAt, actor, action, subjectType, subjectId, details);
    }
}
