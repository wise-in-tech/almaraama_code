package com.almaraama.sites.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.sites.api.UnitType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * A unit (lot) of a site: apartment, shop, parking, storage or other, with its mandatory category (A6). Its own aggregate, so that importing many units
 * never locks the site (UML model, section 4). Never deleted (M2).
 */
@Entity
@Table(schema = "sites", name = "unit")
public class Unit {

    public static final int MIN_FLOOR = -10;
    public static final int MAX_REFERENCE = 50;

    /** What a person enters for a unit. */
    public record Spec(UnitType type, UUID categoryId, String reference, UUID buildingId, UUID outdoorAreaId,
                       Integer floor, UUID annexOfUnitId, boolean saleable, boolean rentable) {
    }

    /**
     * The objects a unit points to, already loaded and checked to belong to the same site: its placement and its
     * category.
     */
    public record Placement(Optional<Building> building, Optional<OutdoorArea> outdoorArea, Optional<Unit> annexOf,
                            UnitCategory category) {
    }

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private UnitType type;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "building_id")
    private UUID buildingId;

    @Column(name = "outdoor_area_id")
    private UUID outdoorAreaId;

    @Column(name = "floor")
    private Integer floor;

    @Column(name = "annex_of_unit_id")
    private UUID annexOfUnitId;

    @Column(name = "saleable", nullable = false)
    private boolean saleable;

    @Column(name = "rentable", nullable = false)
    private boolean rentable;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected Unit() {
    }

    /** A new active unit of the site; see {@link #update} for the rules. */
    public static Unit create(Site site, Spec spec, Placement placement, Instant now) {
        Unit unit = new Unit();
        unit.id = UnitId.newId().value();
        unit.organizationId = site.organizationId().value();
        unit.siteId = site.id().value();
        unit.createdAt = now;
        unit.apply(spec, placement, true, now);
        return unit;
    }

    /**
     * Changes a unit: {@code UNIT_REFERENCE_INVALID}, {@code UNIT_LOCATION_INVALID}, {@code UNIT_FLOOR_INVALID}, {@code
     * UNIT_ANNEX_INVALID} (A4, A6, A7), {@code UNIT_CATEGORY_TYPE_MISMATCH}, {@code UNIT_CATEGORY_ARCHIVED} (A6).
     */
    public void update(Spec spec, Placement placement, boolean active, Instant now) {
        apply(spec, placement, active, now);
    }

    /** Checks the specification against its placement, then sets every field. */
    private void apply(Spec spec, Placement placement, boolean active, Instant now) {
        String cleanReference = Texts.required(spec.reference(), MAX_REFERENCE, "UNIT_REFERENCE_INVALID");
        checkPlacement(spec, placement);
        checkCategory(spec, placement.category());
        this.type = spec.type();
        this.categoryId = placement.category().id();
        this.reference = cleanReference;
        this.buildingId = spec.buildingId();
        this.outdoorAreaId = spec.outdoorAreaId();
        this.floor = spec.floor();
        this.annexOfUnitId = spec.annexOfUnitId();
        this.saleable = spec.saleable();
        this.rentable = spec.rentable();
        this.active = active;
        this.updatedAt = now;
    }

    /** A6 and A4: one place at most, a floor only inside a building, an annex only of an apartment. */
    private void checkPlacement(Spec spec, Placement placement) {
        if (spec.buildingId() != null && spec.outdoorAreaId() != null) {
            throw rule("UNIT_LOCATION_INVALID", "In a building or in an outdoor area, never both (A6)");
        }
        if (spec.floor() != null) {
            Building building = placement.building().orElseThrow(
                    () -> rule("UNIT_FLOOR_INVALID", "A floor only inside a building (A4)"));
            if (spec.floor() < MIN_FLOOR || spec.floor() > building.floorCount()) {
                throw rule("UNIT_FLOOR_INVALID", "Floor outside the building");
            }
        }
        if (spec.annexOfUnitId() != null) {
            Unit main = placement.annexOf().orElseThrow(() -> rule("UNIT_ANNEX_INVALID", "Unknown main unit"));
            boolean annexType = spec.type() == UnitType.PARKING || spec.type() == UnitType.STORAGE;
            if (!annexType || main.type != UnitType.APARTMENT || main.id.equals(id)) {
                throw rule("UNIT_ANNEX_INVALID", "Only a parking or storage can be the annex of an apartment (A7)");
            }
        }
    }

    /**
     * A6: the category classifies this unit type. An archived category is refused unless the unit already has it (an
     * archived category stays on its units, M2).
     */
    private void checkCategory(Spec spec, UnitCategory category) {
        if (category.unitType() != spec.type()) {
            throw rule("UNIT_CATEGORY_TYPE_MISMATCH", "The category is for another unit type (A6)");
        }
        if (!category.active() && !category.id().equals(categoryId)) {
            throw rule("UNIT_CATEGORY_ARCHIVED", "Archived category");
        }
    }

    /** A broken placement rule. */
    private static BusinessException rule(String code, String message) {
        return new BusinessException(code, ErrorCategory.RULE_VIOLATION, message);
    }

    /** Identifier of the unit. */
    public UnitId id() {
        return new UnitId(id);
    }

    /** Organization owning the site. */
    public OrganizationId organizationId() {
        return new OrganizationId(organizationId);
    }

    /** The site of the unit. */
    public SiteId siteId() {
        return new SiteId(siteId);
    }

    /** Apartment, shop, parking or storage. */
    public UnitType type() {
        return type;
    }

    /** Its category (A6). */
    public UUID categoryId() {
        return categoryId;
    }

    /** Reference, unique in the site ignoring case. */
    public String reference() {
        return reference;
    }

    /** The building it is in, if any. */
    public Optional<BuildingId> buildingId() {
        return Optional.ofNullable(buildingId).map(BuildingId::new);
    }

    /** The outdoor area it is in, if any. */
    public Optional<UUID> outdoorAreaId() {
        return Optional.ofNullable(outdoorAreaId);
    }

    /** The floor, only inside a building. */
    public Optional<Integer> floor() {
        return Optional.ofNullable(floor);
    }

    /** The apartment this parking or storage belongs to, if any (A6). */
    public Optional<UnitId> annexOfUnitId() {
        return Optional.ofNullable(annexOfUnitId).map(UnitId::new);
    }

    /** Whether it can be sold. */
    public boolean saleable() {
        return saleable;
    }

    /** Whether it can be rented. */
    public boolean rentable() {
        return rentable;
    }

    /** Whether the unit is in use; never deleted (M2). */
    public boolean active() {
        return active;
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }
}
