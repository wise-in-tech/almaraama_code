package com.almaraama.workflow.domain;

import com.almaraama.shared.id.UuidV7;
import com.almaraama.workflow.api.NodeCategory;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** The position of an instance: one active token in P1 (ADR-036). Left tokens stay as history. */
@Entity
@Table(schema = "workflow", name = "workflow_token")
public class WorkflowToken {

    @Id
    private UUID id;

    @Column(name = "instance_id", nullable = false)
    private UUID instanceId;

    @Column(name = "node_id", nullable = false)
    private UUID nodeId;

    @Column(name = "node_code", nullable = false)
    private String nodeCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "category", nullable = false)
    private NodeCategory category;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "entered_at", nullable = false)
    private Instant enteredAt;

    @Column(name = "left_at")
    private Instant leftAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected WorkflowToken() {
    }

    /** A token entering a step. */
    public static WorkflowToken enter(UUID instanceId, WorkflowNode node, Instant now) {
        WorkflowToken token = new WorkflowToken();
        token.id = UuidV7.next();
        token.instanceId = instanceId;
        token.nodeId = node.id();
        token.nodeCode = node.code();
        token.category = node.category();
        token.active = true;
        token.enteredAt = now;
        return token;
    }

    /** Leaves the step. */
    public void leave(Instant now) {
        active = false;
        leftAt = now;
    }

    /** The instance. */
    public UUID instanceId() {
        return instanceId;
    }

    /** The current step. */
    public UUID nodeId() {
        return nodeId;
    }

    /** When the step was entered (auto-close counts from here, ADR-036). */
    public Instant enteredAt() {
        return enteredAt;
    }
}
