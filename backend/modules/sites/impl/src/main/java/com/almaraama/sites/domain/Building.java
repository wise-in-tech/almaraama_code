package com.almaraama.sites.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
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
 * A building of a site (A2): a villa is a building. Has a use (A5) and a number of floors above the ground floor.
 * May have its own address when it is reached from another street (A1). Never deleted (M2).
 */
@Entity
@Table(schema = "sites", name = "building")
public class Building {

    public static final int MAX_FLOORS = 200;

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "building_use", nullable = false)
    private BuildingUse buildingUse;

    @Column(name = "floor_count", nullable = false)
    private int floorCount;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected Building() {
    }

    /** A new active building of the site; see {@link #update} for the rules. */
    public static Building create(Site site, String name, BuildingUse use, int floorCount, Instant now) {
        Building building = new Building();
        building.id = BuildingId.newId().value();
        building.organizationId = site.organizationId().value();
        building.siteId = site.id().value();
        building.active = true;
        building.createdAt = now;
        building.update(name, use, floorCount, true, now);
        return building;
    }

    /** A5: only the residential use in P1. */
    public void update(String name, BuildingUse use, int floorCount, boolean active, Instant now) {
        if (use != BuildingUse.RESIDENTIAL) {
            throw new BusinessException("BUILDING_USE_NOT_AVAILABLE", ErrorCategory.RULE_VIOLATION,
                    "Only the residential use in P1 (A5)");
        }
        if (floorCount < 0 || floorCount > MAX_FLOORS) {
            throw new BusinessException("BUILDING_FLOOR_COUNT_INVALID", ErrorCategory.VALIDATION, "0 to 200");
        }
        this.name = Texts.required(name, 100, "BUILDING_NAME_INVALID");
        this.buildingUse = use;
        this.floorCount = floorCount;
        this.active = active;
        this.updatedAt = now;
    }

    /** Identifier of the building. */
    public BuildingId id() {
        return new BuildingId(id);
    }

    /** Organization owning the site. */
    public OrganizationId organizationId() {
        return new OrganizationId(organizationId);
    }

    /** The site of the building. */
    public SiteId siteId() {
        return new SiteId(siteId);
    }

    /** Name, unique in the site. */
    public String name() {
        return name;
    }

    /** Use of the building (A5). */
    public BuildingUse buildingUse() {
        return buildingUse;
    }

    /** Floors above the ground floor. */
    public int floorCount() {
        return floorCount;
    }

    /** Whether the building is in use; never deleted (M2). */
    public boolean active() {
        return active;
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }
}
