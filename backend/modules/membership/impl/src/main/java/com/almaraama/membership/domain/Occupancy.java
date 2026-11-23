package com.almaraama.membership.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;

/**
 * A person occupying a unit as owner or tenant, with dates (E1 to E5). Ended, never deleted (E2, M2). Only a tenant
 * can be primary tenant; a unit has at most one active owner (E5) and one active primary tenant (E3), which the use
 * case and the database both enforce.
 */
@Entity
@Table(schema = "membership", name = "occupancy")
public class Occupancy {

    @Id
    private UUID id;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(name = "unit_id", nullable = false)
    private UUID unitId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "role", nullable = false)
    private OccupancyRole role;

    @Column(name = "primary_tenant", nullable = false)
    private boolean primaryTenant;

    @Enumerated(EnumType.STRING)
    @Column(name = "validation_status", nullable = false)
    private ValidationStatus validationStatus;

    @Column(name = "started_on", nullable = false)
    private LocalDate startedOn;

    @Column(name = "ended_on")
    private LocalDate endedOn;

    @Column(name = "end_reason")
    private String endReason;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected Occupancy() {
    }

    /** A new occupancy; {@code OCCUPANCY_PRIMARY_TENANT_NOT_TENANT} for an owner marked primary tenant. */
    public static Occupancy start(PersonId personId, UnitId unitId, OrganizationId organizationId, SiteId siteId,
                                  OccupancyRole role, boolean primaryTenant, LocalDate startedOn,
                                  ValidationStatus validation, PersonId by, Instant now) {
        if (primaryTenant && role != OccupancyRole.TENANT) {
            throw new BusinessException("OCCUPANCY_PRIMARY_TENANT_NOT_TENANT", ErrorCategory.RULE_VIOLATION,
                    "Only a tenant can be primary tenant (E3)");
        }
        Occupancy occupancy = new Occupancy();
        occupancy.id = UuidV7.next();
        occupancy.personId = personId.value();
        occupancy.unitId = unitId.value();
        occupancy.organizationId = organizationId.value();
        occupancy.siteId = siteId.value();
        occupancy.role = role;
        occupancy.primaryTenant = primaryTenant;
        occupancy.startedOn = startedOn;
        occupancy.validationStatus = validation;
        occupancy.createdBy = by.value();
        occupancy.createdAt = now;
        return occupancy;
    }

    /** The person's account became active: the declared occupancy is validated. */
    public void validate() {
        validationStatus = ValidationStatus.VALIDATED;
    }

    /** E2: an occupancy ends, it is never deleted. */
    public void end(LocalDate on, String reason) {
        if (endedOn != null) {
            throw new BusinessException("OCCUPANCY_ALREADY_ENDED", ErrorCategory.RULE_VIOLATION, "Already ended");
        }
        if (on.isBefore(startedOn)) {
            throw new BusinessException("OCCUPANCY_END_BEFORE_START", ErrorCategory.RULE_VIOLATION,
                    "Ends before it started");
        }
        endedOn = on;
        endReason = reason.strip();
    }

    /** Whether it has not ended. */
    public boolean active() {
        return endedOn == null;
    }

    /** Identifier of the occupancy. */
    public UUID id() {
        return id;
    }

    /** The occupant. */
    public PersonId personId() {
        return new PersonId(personId);
    }

    /** The unit occupied. */
    public UnitId unitId() {
        return new UnitId(unitId);
    }

    /** Organization owning the site. */
    public OrganizationId organizationId() {
        return new OrganizationId(organizationId);
    }

    /** The site of the unit. */
    public SiteId siteId() {
        return new SiteId(siteId);
    }

    /** Owner or tenant. */
    public OccupancyRole role() {
        return role;
    }

    /** Whether this tenant is the unit's primary tenant (E3). */
    public boolean primaryTenant() {
        return primaryTenant;
    }

    /** Declared, or validated once the account is active. */
    public ValidationStatus validationStatus() {
        return validationStatus;
    }

    /** First day of the occupancy. */
    public LocalDate startedOn() {
        return startedOn;
    }

    /** Last day, once ended. */
    public Optional<LocalDate> endedOn() {
        return Optional.ofNullable(endedOn);
    }

    /** Why it ended. */
    public Optional<String> endReason() {
        return Optional.ofNullable(endReason);
    }
}
