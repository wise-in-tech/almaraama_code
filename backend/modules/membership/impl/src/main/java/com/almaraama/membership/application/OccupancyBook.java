package com.almaraama.membership.application;

import com.almaraama.membership.api.events.OccupancyStartedV1;
import com.almaraama.membership.domain.Occupancy;
import com.almaraama.membership.domain.OccupancyRepository;
import com.almaraama.membership.domain.OccupancyRole;
import com.almaraama.membership.domain.ValidationStatus;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.sites.api.BuildingSummary;
import com.almaraama.sites.api.SitesApi;
import com.almaraama.sites.api.UnitSummary;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneId;
import java.util.Collection;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

/**
 * Starts occupancies with the rules of E1 to E5, for invitations, approvals and the occupancy endpoints, and checks
 * that buildings and units belong to the site.
 */
@Component
class OccupancyBook {

    static final ZoneId ALGIERS = ZoneId.of("Africa/Algiers");

    private final OccupancyRepository occupancies;
    private final SitesApi sites;
    private final ApplicationEventPublisher events;

    /**
     * @param occupancies the occupancies
     * @param sites units and buildings of the site
     * @param events publishes {@code OccupancyStartedV1}
     */
    OccupancyBook(OccupancyRepository occupancies, SitesApi sites, ApplicationEventPublisher events) {
        this.occupancies = occupancies;
        this.sites = sites;
        this.events = events;
    }

    /**
     * Starts an occupancy: {@code UNIT_NOT_IN_SITE}, {@code OCCUPANCY_EXISTS}, {@code OCCUPANCY_OWNER_EXISTS} (E5),
     * {@code OCCUPANCY_PRIMARY_TENANT_EXISTS} (E3). Publishes {@code OccupancyStartedV1}.
     */
    Occupancy start(PersonId person, OrganizationId organization, SiteId site, OccupancyRequest request,
                    ValidationStatus validation, PersonId by, Instant now) {
        UnitSummary unit = unitOf(site, request.unitId());
        if (occupancies.active(person, unit.id()).isPresent()) {
            throw new BusinessException("OCCUPANCY_EXISTS", ErrorCategory.CONFLICT, "Already occupies the unit");
        }
        if (request.role() == OccupancyRole.OWNER && occupancies.activeOwner(unit.id()).isPresent()) {
            throw new BusinessException("OCCUPANCY_OWNER_EXISTS", ErrorCategory.CONFLICT,
                    "A unit has one owner (E5)");
        }
        if (request.primaryTenant() && occupancies.activePrimaryTenant(unit.id()).isPresent()) {
            throw new BusinessException("OCCUPANCY_PRIMARY_TENANT_EXISTS", ErrorCategory.CONFLICT,
                    "A unit has one primary tenant (E3)");
        }
        LocalDate startedOn = request.startedOn() != null ? request.startedOn() : LocalDate.ofInstant(now, ALGIERS);
        Occupancy occupancy = occupancies.save(Occupancy.start(person, unit.id(), organization, site,
                request.role(), request.primaryTenant(), startedOn, validation, by, now));
        events.publishEvent(new OccupancyStartedV1(occupancy.id(), organization, site, unit.id(), person,
                occupancy.role().name(), now));
        return occupancy;
    }

    /** An active unit of the site, or {@code UNIT_NOT_IN_SITE}. */
    UnitSummary unitOf(SiteId site, UnitId unitId) {
        return sites.findUnit(unitId).filter(u -> u.siteId().equals(site) && u.active())
                .orElseThrow(() -> new BusinessException("UNIT_NOT_IN_SITE", ErrorCategory.RULE_VIOLATION,
                        "Unit not in this site"));
    }

    /** The given buildings must be active buildings of the site. */
    Set<BuildingId> buildingsOf(SiteId site, Collection<UUID> buildingIds) {
        Set<UUID> known = sites.activeBuildings(site).stream().map(b -> b.id().value()).collect(Collectors.toSet());
        if (!known.containsAll(buildingIds)) {
            throw new BusinessException("BUILDING_NOT_IN_SITE", ErrorCategory.RULE_VIOLATION, "Unknown building");
        }
        return buildingIds.stream().map(BuildingId::new).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** C7 default: a resident sees the buildings of their units. */
    Set<BuildingId> buildingsOfUnits(SiteId site, List<OccupancyRequest> requests) {
        return requests.stream().map(r -> unitOf(site, r.unitId()))
                .flatMap(u -> u.buildingId().stream())
                .collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Active buildings of the site. */
    List<BuildingSummary> buildings(SiteId site) {
        return sites.activeBuildings(site);
    }
}
