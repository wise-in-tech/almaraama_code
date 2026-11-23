package com.almaraama.sites.application;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.events.UnitCreatedV1;
import com.almaraama.sites.api.events.UnitUpdatedV1;
import com.almaraama.sites.application.Views.UnitView;
import com.almaraama.sites.domain.Building;
import com.almaraama.sites.domain.BuildingRepository;
import com.almaraama.sites.domain.OutdoorArea;
import com.almaraama.sites.domain.OutdoorAreaRepository;
import com.almaraama.sites.domain.Site;
import com.almaraama.sites.domain.Unit;
import com.almaraama.sites.domain.UnitCategory;
import com.almaraama.sites.domain.UnitCategoryRepository;
import com.almaraama.sites.domain.UnitRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Units of a site (A6, A7, E1): apartments, shops, parkings, storages and others, their category and annexes. */
@Service
public class UnitService {

    private final UnitRepository units;
    private final BuildingRepository buildings;
    private final OutdoorAreaRepository areas;
    private final UnitCategoryRepository categories;
    private final SiteService sites;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the ports and collaborators of the unit use cases. */
    UnitService(UnitRepository units, BuildingRepository buildings, OutdoorAreaRepository areas,
                UnitCategoryRepository categories, SiteService sites, ApplicationEventPublisher events, Clock clock) {
        this.units = units;
        this.buildings = buildings;
        this.areas = areas;
        this.categories = categories;
        this.sites = sites;
        this.events = events;
        this.clock = clock;
    }

    /** Units of a visible site matching the criteria, with their category; needs {@code unit:read}. */
    @Transactional(readOnly = true)
    public PageResult<UnitView> list(Perimeter perimeter, SiteId siteId, UnitRepository.Filter criteria,
                                     PageQuery page) {
        Site site = sites.visible(perimeter, siteId);
        perimeter.require(Permission.UNIT_READ, site.organizationId(), siteId);
        PageResult<Unit> found = units.search(siteId, criteria, page);
        Map<UUID, UnitCategory> byId = categories.findAllById(found.items().stream().map(Unit::categoryId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(UnitCategory::id, c -> c));
        return found.map(unit -> new UnitView(unit, byId.get(unit.categoryId())));
    }

    /**
     * One unit with its category: with {@code unit:read} on the site, or for its own occupant (C4); {@code NOT_FOUND}
     * otherwise, and outside the caller's buildings (C7).
     */
    @Transactional(readOnly = true)
    public UnitView get(Perimeter perimeter, UnitId unitId) {
        Unit unit = units.findById(unitId).orElseThrow(OrganizationService::notFound);
        Site site = sites.visible(perimeter, unit.siteId());
        boolean occupant = perimeter.site(site.id()).map(a -> a.occupiedUnitIds().contains(unitId)).orElse(false);
        if (!occupant && !perimeter.can(Permission.UNIT_READ, site.organizationId(), site.id())) {
            throw OrganizationService.notFound();
        }
        unit.buildingId().ifPresent(building -> {
            if (!perimeter.canSeeBuilding(site.organizationId(), site.id(), building)) {
                throw OrganizationService.notFound();
            }
        });
        return view(unit);
    }

    /**
     * Creates a unit; needs {@code unit:create}; {@code UNIT_REFERENCE_TAKEN}, the placement rules of A6 and A7 and the
     * category rules ({@code UNIT_CATEGORY_TYPE_MISMATCH}, {@code UNIT_CATEGORY_ARCHIVED}, {@code
     * UNIT_CATEGORY_NOT_IN_SCOPE}). Publishes {@code UnitCreatedV1}.
     */
    @Transactional
    public UnitView create(Perimeter perimeter, SiteId siteId, Unit.Spec spec) {
        Site site = sites.visible(perimeter, siteId);
        perimeter.require(Permission.UNIT_CREATE, site.organizationId(), siteId);
        requireFreeReference(siteId, spec.reference(), null);
        Instant now = clock.instant();
        Unit unit = units.save(Unit.create(site, spec, placement(site, spec), now));
        events.publishEvent(new UnitCreatedV1(unit.id(), siteId, site.organizationId(), unit.buildingId(),
                unit.type(), unit.reference(), perimeter.actor(), now));
        return view(unit);
    }

    /**
     * Changes or archives a unit; needs {@code unit:update}, and {@code unit:archive} to change {@code active}; {@code
     * VERSION_CONFLICT} and the rules of {@link #create}. Publishes {@code UnitUpdatedV1}.
     */
    @Transactional
    public UnitView update(Perimeter perimeter, UnitId unitId, Unit.Spec spec, boolean active, long version) {
        Unit unit = units.findById(unitId).orElseThrow(OrganizationService::notFound);
        Site site = sites.visible(perimeter, unit.siteId());
        perimeter.require(Permission.UNIT_UPDATE, site.organizationId(), site.id());
        Archiving.requireIfChanged(perimeter, Permission.UNIT_ARCHIVE, unit.active(), active, site.organizationId(),
                site.id());
        Versions.check(version, unit.version());
        requireFreeReference(site.id(), spec.reference(), unitId);
        Instant now = clock.instant();
        unit.update(spec, placement(site, spec), active, now);
        units.save(unit);
        events.publishEvent(new UnitUpdatedV1(unit.id(), site.id(), site.organizationId(), unit.buildingId(),
                unit.type(), unit.reference(), unit.active(), perimeter.actor(), now));
        return view(unit);
    }

    /** A unit with its category. */
    private UnitView view(Unit unit) {
        return new UnitView(unit, categories.findById(unit.categoryId())
                .orElseThrow(() -> new IllegalStateException("Unit without category: " + unit.id())));
    }

    /** Loads what the unit points to, checking it belongs to the same site; the category must be usable there. */
    Unit.Placement placement(Site site, Unit.Spec spec) {
        Optional<Building> building = Optional.ofNullable(spec.buildingId())
                .map(id -> buildings.findById(new BuildingId(id))
                        .filter(b -> b.siteId().equals(site.id()))
                        .orElseThrow(() -> rule("BUILDING_NOT_IN_SITE")));
        Optional<OutdoorArea> area = Optional.ofNullable(spec.outdoorAreaId())
                .map(id -> areas.findById(id)
                        .filter(a -> a.siteId().equals(site.id()))
                        .orElseThrow(() -> rule("OUTDOOR_AREA_NOT_IN_SITE")));
        Optional<Unit> annexOf = Optional.ofNullable(spec.annexOfUnitId())
                .map(id -> units.findById(new UnitId(id))
                        .filter(u -> u.siteId().equals(site.id()))
                        .orElseThrow(() -> rule("UNIT_ANNEX_INVALID")));
        UnitCategory category = Optional.ofNullable(spec.categoryId()).flatMap(categories::findById)
                .filter(c -> c.usableOn(site.organizationId(), site.id()))
                .orElseThrow(() -> rule("UNIT_CATEGORY_NOT_IN_SCOPE"));
        return new Unit.Placement(building, area, annexOf, category);
    }

    /** {@code UNIT_REFERENCE_TAKEN} when another unit of the site has this reference, ignoring case. */
    private void requireFreeReference(SiteId siteId, String reference, UnitId except) {
        if (reference == null) {
            return;
        }
        units.findByReference(siteId, reference)
                .filter(existing -> !existing.id().equals(except))
                .ifPresent(existing -> {
                    throw new BusinessException("UNIT_REFERENCE_TAKEN", ErrorCategory.CONFLICT, "Reference used");
                });
    }

    /**
     * A placement rule broken ({@code BUILDING_NOT_IN_SITE}, {@code OUTDOOR_AREA_NOT_IN_SITE}, {@code
     * UNIT_ANNEX_INVALID}, {@code UNIT_CATEGORY_NOT_IN_SCOPE}).
     */
    private static BusinessException rule(String code) {
        return new BusinessException(code, ErrorCategory.RULE_VIOLATION, code);
    }
}
