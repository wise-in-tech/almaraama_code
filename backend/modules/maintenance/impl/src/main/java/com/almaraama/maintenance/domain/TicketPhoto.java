package com.almaraama.maintenance.domain;

import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A photo of a ticket (H10): a stored file of the {@code files} module. Three at most per ticket. */
@Entity
@Table(schema = "maintenance", name = "ticket_photo")
public class TicketPhoto {

    /** Most photos of a ticket (H10). */
    public static final int MAX_PER_TICKET = 3;

    @Id
    private UUID id;

    @Column(name = "ticket_id", nullable = false)
    private UUID ticketId;

    @Column(name = "file_id", nullable = false)
    private UUID fileId;

    @Column(name = "added_by", nullable = false)
    private UUID addedBy;

    @Column(name = "added_at", nullable = false)
    private Instant addedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected TicketPhoto() {
    }

    /** A photo added by a person. */
    public static TicketPhoto of(UUID ticketId, UUID fileId, UUID addedBy, Instant now) {
        TicketPhoto photo = new TicketPhoto();
        photo.id = UuidV7.next();
        photo.ticketId = ticketId;
        photo.fileId = fileId;
        photo.addedBy = addedBy;
        photo.addedAt = now;
        return photo;
    }

    /** Identifier. */
    public UUID id() {
        return id;
    }

    /** The ticket. */
    public UUID ticketId() {
        return ticketId;
    }

    /** The stored file. */
    public UUID fileId() {
        return fileId;
    }

    /** When added. */
    public Instant addedAt() {
        return addedAt;
    }
}
