package com.almaraama.maintenance.domain;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.actor.ActorType;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A technician on a ticket (J1): created by the routing in sprint 3 (H4), added or removed by a manager from sprint 4.
 * Ended, never deleted (M2). An active assignment makes the ticket visible to the technician (relation, ADR-037).
 */
@Entity
@Table(schema = "maintenance", name = "ticket_assignment")
public class TicketAssignment {

    @Id
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "technician_id", nullable = false)
    private UUID technicianId;

    @Column(name = "origin", nullable = false)
    private String origin;

    @Enumerated(EnumType.STRING)
    @Column(name = "added_by_type", nullable = false)
    private ActorType addedByType;

    @Column(name = "added_by_id", nullable = false)
    private UUID addedById;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Column(name = "removed_at")
    private Instant removedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected TicketAssignment() {
    }

    /** A technician designated by the routing of the ticket's type (H4). */
    public static TicketAssignment routed(UUID ticketId, PersonId technician, Actor by, Instant now) {
        TicketAssignment assignment = new TicketAssignment();
        assignment.id = UuidV7.next();
        assignment.ticketId = ticketId;
        assignment.technicianId = technician.value();
        assignment.origin = "ROUTING";
        assignment.addedByType = by.type();
        assignment.addedById = by.id();
        assignment.addedAt = now;
        return assignment;
    }

    /** The ticket. */
    public UUID ticketId() {
        return ticketId;
    }

    /** The technician. */
    public PersonId technicianId() {
        return new PersonId(technicianId);
    }

    /** Whether still on the ticket. */
    public boolean active() {
        return removedAt == null;
    }
}
