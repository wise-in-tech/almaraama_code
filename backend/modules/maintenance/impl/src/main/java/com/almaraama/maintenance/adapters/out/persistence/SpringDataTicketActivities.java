package com.almaraama.maintenance.adapters.out.persistence;

import com.almaraama.maintenance.domain.TicketActivity;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data access to {@code maintenance.ticket_activity}: insert and read only (H9). */
interface SpringDataTicketActivities extends Repository<TicketActivity, UUID> {

    /** The activities of a ticket, oldest first. */
    List<TicketActivity> findByTicketIdOrderByOccurredAtAsc(UUID ticketId);

    /** Appends an activity. */
    TicketActivity save(TicketActivity activity);
}
