package com.almaraama.audit.adapters.out.persistence;

import com.almaraama.audit.domain.AuditEntry;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;
import org.springframework.data.domain.Persistable;

/**
 * Persistence shape of an audit entry. Separate from the domain record because {@code details} is a {@code jsonb}
 * column, which needs a Hibernate-specific mapping the domain must not know (CF-8).
 */
@Entity
@Table(schema = "audit", name = "audit_entry")
class AuditEntryRow implements Persistable<UUID> {

    @Id
    private UUID id;

    @Column(name = "occurred_at", nullable = false, updatable = false)
    private Instant occurredAt;

    @Column(name = "recorded_at", nullable = false, updatable = false)
    private Instant recordedAt;

    @Column(name = "actor_type", nullable = false, updatable = false)
    private String actorType;

    @Column(name = "actor_id", nullable = false, updatable = false)
    private UUID actorId;

    /** The support session the actor acted in (C12, ADR-035); {@code null} outside support. */
    @Column(name = "support_session_id", updatable = false)
    private UUID supportSessionId;

    @Column(name = "action", nullable = false, updatable = false)
    private String action;

    @Column(name = "subject_type", updatable = false)
    private String subjectType;

    @Column(name = "subject_id", updatable = false)
    private UUID subjectId;

    @JdbcTypeCode(SqlTypes.JSON)
    @Column(name = "details", nullable = false, updatable = false)
    private Map<String, String> details;

    /** Entries are only ever inserted: tells Spring Data to persist without looking the row up first. */
    @Transient
    private boolean fresh = true;

    /** For JPA only. */
    protected AuditEntryRow() {
    }

    /** Identifier of the line, required by Spring Data's {@code Persistable}. */
    @Override
    public UUID getId() {
        return id;
    }

    /** Always new: a line is only ever inserted, never updated (M2). */
    @Override
    public boolean isNew() {
        return fresh;
    }

    /** The row for a journal entry, with the time it was recorded. */
    static AuditEntryRow from(AuditEntry entry, Instant recordedAt) {
        AuditEntryRow row = new AuditEntryRow();
        row.id = entry.id();
        row.occurredAt = entry.occurredAt();
        row.recordedAt = recordedAt;
        row.actorType = entry.actor().type().name();
        row.actorId = entry.actor().id();
        row.supportSessionId = entry.actor().supportSessionId();
        row.action = entry.action();
        row.subjectType = entry.subjectType();
        row.subjectId = entry.subjectId();
        row.details = entry.details();
        return row;
    }
}
