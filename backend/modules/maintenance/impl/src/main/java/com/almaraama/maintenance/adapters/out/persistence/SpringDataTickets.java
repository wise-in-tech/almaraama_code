package com.almaraama.maintenance.adapters.out.persistence;

import com.almaraama.maintenance.domain.Ticket;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data access to {@code maintenance.ticket}; no delete (M2). */
interface SpringDataTickets extends Repository<Ticket, UUID> {

    /** Loads a ticket. */
    Optional<Ticket> findById(UUID id);

    /** Inserts or updates a ticket. */
    Ticket save(Ticket ticket);
}
