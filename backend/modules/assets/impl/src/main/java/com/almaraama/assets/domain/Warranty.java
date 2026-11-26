package com.almaraama.assets.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * A warranty of an equipment (G3): dates and the number of days of alert before its end. The alert itself is sent
 * from sprint 4 (K4, I5). Archived, never deleted (M2).
 */
@Entity
@Table(schema = "assets", name = "warranty")
public class Warranty {

    @Id
    private UUID id;

    @Column(name = "asset_id", nullable = false)
    private UUID assetId;

    @Column(name = "start_date", nullable = false)
    private LocalDate startDate;

    @Column(name = "end_date", nullable = false)
    private LocalDate endDate;

    @Column(name = "alert_days_before", nullable = false)
    private int alertDaysBefore;

    @Column(name = "document_id")
    private UUID documentId;

    @Column(name = "note")
    private String note;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected Warranty() {
    }

    /** A new warranty. {@code WARRANTY_DATES_INVALID} when it ends before it starts or the alert is out of 0..365. */
    public static Warranty create(UUID assetId, LocalDate start, LocalDate end, int alertDaysBefore,
                                  Optional<UUID> documentId, String note, Instant now) {
        Warranty warranty = new Warranty();
        warranty.id = UuidV7.next();
        warranty.assetId = assetId;
        warranty.createdAt = now;
        warranty.update(start, end, alertDaysBefore, documentId, note, true, now);
        return warranty;
    }

    /** Corrects or archives the warranty. Same checks as {@link #create}. */
    public void update(LocalDate start, LocalDate end, int alertDaysBefore, Optional<UUID> documentId, String note,
                       boolean active, Instant now) {
        if (start == null || end == null || end.isBefore(start) || alertDaysBefore < 0 || alertDaysBefore > 365) {
            throw new BusinessException("WARRANTY_DATES_INVALID", ErrorCategory.RULE_VIOLATION, "Dates or alert");
        }
        this.startDate = start;
        this.endDate = end;
        this.alertDaysBefore = alertDaysBefore;
        this.documentId = documentId.orElse(null);
        this.note = Labels.optional(note, 300, "WARRANTY_DATES_INVALID");
        this.active = active;
        this.updatedAt = now;
    }

    /** Identifier. */
    public UUID id() {
        return id;
    }

    /** The equipment. */
    public UUID assetId() {
        return assetId;
    }

    /** First day covered. */
    public LocalDate startDate() {
        return startDate;
    }

    /** Last day covered. */
    public LocalDate endDate() {
        return endDate;
    }

    /** Days of alert before the end (K4, sprint 4). */
    public int alertDaysBefore() {
        return alertDaysBefore;
    }

    /** Supporting document among the equipment's. */
    public Optional<UUID> documentId() {
        return Optional.ofNullable(documentId);
    }

    /** Free note. */
    public Optional<String> note() {
        return Optional.ofNullable(note);
    }

    /** Whether shown and used for alerts. */
    public boolean active() {
        return active;
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }
}
