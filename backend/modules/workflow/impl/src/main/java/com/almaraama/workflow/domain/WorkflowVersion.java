package com.almaraama.workflow.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A version of a circuit (ADR-036): {@code DRAFT}, then {@code PUBLISHED} and frozen, then {@code RETIRED} when a newer
 * one is published. Instances finish on the version they started on.
 */
@Entity
@Table(schema = "workflow", name = "workflow_version")
public class WorkflowVersion {

    /** A draft, editable in the tables. */
    public static final String DRAFT = "DRAFT";
    /** The version new instances start on. */
    public static final String PUBLISHED = "PUBLISHED";
    /** Replaced by a newer version. */
    public static final String RETIRED = "RETIRED";

    @Id
    private UUID id;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "number", nullable = false)
    private int number;

    @Column(name = "status", nullable = false)
    private String status;

    @Column(name = "published_at")
    private Instant publishedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected WorkflowVersion() {
    }

    /** Publishes a checked draft. {@code WORKFLOW_VERSION_PUBLISHED} when not a draft. */
    public void publish(Instant now) {
        if (!DRAFT.equals(status)) {
            throw new BusinessException("WORKFLOW_VERSION_PUBLISHED", ErrorCategory.CONFLICT, "Not a draft");
        }
        status = PUBLISHED;
        publishedAt = now;
    }

    /** Retires the version when a newer one is published. */
    public void retire() {
        if (PUBLISHED.equals(status)) {
            status = RETIRED;
        }
    }

    /** Identifier. */
    public UUID id() {
        return id;
    }

    /** The circuit. */
    public UUID workflowId() {
        return workflowId;
    }

    /** Number, from 1. */
    public int number() {
        return number;
    }

    /** {@code DRAFT}, {@code PUBLISHED} or {@code RETIRED}. */
    public String status() {
        return status;
    }

    /** When it was published. */
    public Optional<Instant> publishedAt() {
        return Optional.ofNullable(publishedAt);
    }
}
