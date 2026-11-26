package com.almaraama.maintenance.adapters.out.persistence;

import com.almaraama.maintenance.domain.TicketPhoto;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code maintenance.ticket_photo}; no delete (M2). */
interface SpringDataTicketPhotos extends Repository<TicketPhoto, UUID> {

    /** The photos of a ticket, oldest first. */
    List<TicketPhoto> findByTicketIdOrderByAddedAtAsc(UUID ticketId);

    /** Ticket identifiers and photo counts. */
    @Query("select p.ticketId, count(p) from TicketPhoto p where p.ticketId in :tickets group by p.ticketId")
    List<Object[]> counts(@Param("tickets") Collection<UUID> tickets);

    /** Inserts a photo. */
    TicketPhoto save(TicketPhoto photo);
}
