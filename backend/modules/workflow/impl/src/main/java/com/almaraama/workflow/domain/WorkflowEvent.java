package com.almaraama.workflow.domain;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.actor.ActorType;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** One entry of the history of an instance (H9): append only, never changed (no UPDATE for the app role). */
@Entity
@Table(schema = "workflow", name = "workflow_event")
public class WorkflowEvent {

    @Id
    private UUID id;

    @Column(name = "instance_id", nullable = false)
    private UUID instanceId;

    @Column(name = "transition_id")
    private UUID transitionId;

    @Column(name = "transition_code")
    private String transitionCode;

    @Column(name = "from_node_code")
    private String fromNodeCode;

    @Column(name = "to_node_code", nullable = false)
    private String toNodeCode;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    private ActorType actorType;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "support_session_id")
    private UUID supportSessionId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "payload", nullable = false)
    private String payload;

    /** For JPA only. */
    protected WorkflowEvent() {
    }

    /**
     * An entry: the start ({@code transition} empty) or a passage.
     *
     * @param payloadJson reason and comment as JSON text
     */
    public static WorkflowEvent of(UUID instanceId, Optional<WorkflowTransition> transition, Optional<String> from,
                                   String to, Actor actor, String payloadJson, Instant now) {
        WorkflowEvent event = new WorkflowEvent();
        event.id = UuidV7.next();
        event.instanceId = instanceId;
        event.transitionId = transition.map(WorkflowTransition::id).orElse(null);
        event.transitionCode = transition.map(WorkflowTransition::code).orElse(null);
        event.fromNodeCode = from.orElse(null);
        event.toNodeCode = to;
        event.actorType = actor.type();
        event.actorId = actor.id();
        event.supportSessionId = actor.supportSessionId();
        event.occurredAt = now;
        event.payload = payloadJson;
        return event;
    }

    /** The instance. */
    public UUID instanceId() {
        return instanceId;
    }

    /** The passage taken, empty for the start. */
    public Optional<String> transitionCode() {
        return Optional.ofNullable(transitionCode);
    }

    /** The step left, empty for the start. */
    public Optional<String> fromNodeCode() {
        return Optional.ofNullable(fromNodeCode);
    }

    /** The step entered. */
    public String toNodeCode() {
        return toNodeCode;
    }

    /** Who did it. */
    public Actor actor() {
        return new Actor(actorType, actorId, supportSessionId);
    }

    /** When. */
    public Instant occurredAt() {
        return occurredAt;
    }

    /** Reason and comment as JSON text. */
    public String payloadJson() {
        return payload;
    }
}
