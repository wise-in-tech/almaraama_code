package com.almaraama.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.util.Optional;
import java.util.UUID;

/**
 * A passage between two steps (ADR-036): at most one permission, and conditions from the catalogue (JSON text). One row
 * per origin step; rows of a passage share its code.
 */
@Entity
@Table(schema = "workflow", name = "workflow_transition")
public class WorkflowTransition {

    @Id
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "from_node_id", nullable = false)
    private UUID fromNodeId;

    @Column(name = "to_node_id", nullable = false)
    private UUID toNodeId;

    @Column(name = "permission")
    private String permission;

    @Column(name = "guards", nullable = false)
    private String guards;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    /** For JPA only. */
    protected WorkflowTransition() {
    }

    /** Identifier. */
    public UUID id() {
        return id;
    }

    /** The version. */
    public UUID versionId() {
        return versionId;
    }

    /** Passage code. */
    public String code() {
        return code;
    }

    /** Origin step. */
    public UUID fromNodeId() {
        return fromNodeId;
    }

    /** Target step. */
    public UUID toNodeId() {
        return toNodeId;
    }

    /** The permission needed, when any ({@code ticket:close}). */
    public Optional<String> permission() {
        return Optional.ofNullable(permission).filter(p -> !p.isBlank());
    }

    /** Conditions as a JSON array of {@code {"code": ..., "config": {...}}}. */
    public String guardsJson() {
        return guards;
    }

    /** Display order. */
    public int sortOrder() {
        return sortOrder;
    }
}
