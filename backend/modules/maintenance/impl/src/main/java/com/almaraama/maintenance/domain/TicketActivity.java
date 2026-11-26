package com.almaraama.maintenance.domain;

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
import java.util.UUID;

/** A change of a ticket other than its steps (H9). Append only. */
@Entity
@Table(schema = "maintenance", name = "ticket_activity")
public class TicketActivity {

    @Id
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private TicketActivityType type;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false)
    private ActorType actorType;

    @Column(name = "actor_id", nullable = false)
    private UUID actorId;

    @Column(name = "occurred_at", nullable = false)
    private Instant occurredAt;

    @Column(name = "details", nullable = false)
    private String details;

    /** For JPA only. */
    protected TicketActivity() {
    }

    /**
     * An entry.
     *
     * @param subject the object of the change (the routed technician's identifier), or {@code null}
     */
    public static TicketActivity of(UUID ticketId, TicketActivityType type, Actor actor, UUID subject, Instant now) {
        TicketActivity activity = new TicketActivity();
        activity.id = UuidV7.next();
        activity.ticketId = ticketId;
        activity.type = type;
        activity.actorType = actor.type();
        activity.actorId = actor.id();
        activity.occurredAt = now;
        activity.details = subject == null ? "{}" : "{\"subject\":\"" + subject + "\"}";
        return activity;
    }

    /** Kind of change. */
    public TicketActivityType type() {
        return type;
    }

    /** Who did it. */
    public Actor actor() {
        return new Actor(actorType, actorId);
    }

    /** When. */
    public Instant occurredAt() {
        return occurredAt;
    }

    /** The object of the change, when any. */
    public java.util.Optional<UUID> subject() {
        int at = details.indexOf("\"subject\":\"");
        return at < 0 ? java.util.Optional.empty()
                : java.util.Optional.of(UUID.fromString(details.substring(at + 11, at + 47)));
    }
}
