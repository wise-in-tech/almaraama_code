package com.almaraama.maintenance.adapters.out.persistence;

import com.almaraama.maintenance.domain.TicketAssignment;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code maintenance.ticket_assignment}; ended, never deleted (M2). */
interface SpringDataTicketAssignments extends Repository<TicketAssignment, UUID> {

    /** The active assignments of a ticket. */
    @Query("select a from TicketAssignment a where a.ticketId = :ticket and a.removedAt is null order by a.addedAt")
    List<TicketAssignment> active(@Param("ticket") UUID ticket);

    /** Whether the person is actively assigned. */
    @Query("select count(a) > 0 from TicketAssignment a where a.ticketId = :ticket and a.technicianId = :person "
            + "and a.removedAt is null")
    boolean assigned(@Param("ticket") UUID ticket, @Param("person") UUID person);

    /** Inserts or updates an assignment. */
    TicketAssignment save(TicketAssignment assignment);
}
