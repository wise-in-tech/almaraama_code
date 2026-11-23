package com.almaraama.membership.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A platform administrator's access to one organization for support (C12, ADR-035): a mandatory reason, one hour,
 * the promoteur's permissions on that organization only while it is open; every action done meanwhile carries the
 * session in the audit log. Ended early or expired, never deleted (M2).
 */
@Entity
@Table(schema = "membership", name = "support_session")
public class SupportSession {

    public static final Duration LENGTH = Duration.ofHours(1);
    public static final int MIN_REASON = 10;
    public static final int MAX_REASON = 500;

    /** Open, ended by the administrator, or expired (computed). Glossary {@code SupportSessionStatus}. */
    public enum Status {
        OPEN, ENDED, EXPIRED
    }

    @Id
    private UUID id;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "reason", nullable = false)
    private String reason;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected SupportSession() {
    }

    /** Opens a session for one hour; {@code SUPPORT_REASON_INVALID} for a reason outside 10 to 500 characters. */
    public static SupportSession start(PersonId administrator, OrganizationId organization, String reason,
                                       Instant now) {
        String clean = reason == null ? "" : reason.strip();
        if (clean.length() < MIN_REASON || clean.length() > MAX_REASON) {
            throw new BusinessException("SUPPORT_REASON_INVALID", ErrorCategory.VALIDATION,
                    "A reason of 10 to 500 characters");
        }
        SupportSession session = new SupportSession();
        session.id = UuidV7.next();
        session.personId = administrator.value();
        session.organizationId = organization.value();
        session.reason = clean;
        session.startedAt = now;
        session.expiresAt = now.plus(LENGTH);
        return session;
    }

    /** Ends the session now; nothing changes when it already ended or expired. Returns whether it was open. */
    public boolean end(Instant now) {
        if (!isOpen(now)) {
            return false;
        }
        endedAt = now;
        return true;
    }

    /** Whether the session still gives access. */
    public boolean isOpen(Instant now) {
        return endedAt == null && now.isBefore(expiresAt);
    }

    /** Open, ended or expired at this instant. */
    public Status status(Instant now) {
        if (endedAt != null) {
            return Status.ENDED;
        }
        return now.isBefore(expiresAt) ? Status.OPEN : Status.EXPIRED;
    }

    /** Identifier of the session, carried by every action done in it. */
    public UUID id() {
        return id;
    }

    /** The administrator. */
    public PersonId personId() {
        return new PersonId(personId);
    }

    /** The organization opened for support. */
    public OrganizationId organizationId() {
        return new OrganizationId(organizationId);
    }

    /** Why the access was needed. */
    public String reason() {
        return reason;
    }

    /** When it was opened. */
    public Instant startedAt() {
        return startedAt;
    }

    /** When it stops by itself. */
    public Instant expiresAt() {
        return expiresAt;
    }

    /** When the administrator ended it. */
    public Optional<Instant> endedAt() {
        return Optional.ofNullable(endedAt);
    }
}
