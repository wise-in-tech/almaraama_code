package com.almaraama.sites.application;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.sites.api.OrganizationAdminView;
import com.almaraama.sites.api.events.OrganizationDeactivatedV1;
import com.almaraama.sites.api.events.OrganizationReactivatedV1;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.sites.api.BuildingSummary;
import com.almaraama.sites.api.CreateOrganization;
import com.almaraama.sites.api.OrganizationSummary;
import com.almaraama.sites.api.SiteSummary;
import com.almaraama.sites.api.SitesApi;
import com.almaraama.sites.api.UnitSummary;
import com.almaraama.sites.api.events.OrganizationCreatedV1;
import com.almaraama.sites.domain.Building;
import com.almaraama.sites.domain.BuildingRepository;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.sites.domain.Organization;
import com.almaraama.sites.domain.ReferenceData;
import com.almaraama.sites.domain.OrganizationRepository;
import com.almaraama.sites.domain.Site;
import com.almaraama.sites.domain.SiteRepository;
import com.almaraama.sites.domain.Unit;
import com.almaraama.sites.domain.UnitRepository;
import java.time.Clock;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Implementation of the public port of the module (CF-1). Reads do not check perimeters: callers do. */
@Service
class SitesApiService implements SitesApi {

    private static final Logger LOG = LoggerFactory.getLogger(SitesApiService.class);

    private final OrganizationRepository organizations;
    private final SiteRepository sites;
    private final BuildingRepository buildings;
    private final UnitRepository units;
    private final ReferenceData reference;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the repositories read by other modules. */
    SitesApiService(OrganizationRepository organizations, SiteRepository sites, BuildingRepository buildings,
                    UnitRepository units, ReferenceData reference, ApplicationEventPublisher events, Clock clock) {
        this.reference = reference;
        this.organizations = organizations;
        this.sites = sites;
        this.buildings = buildings;
        this.units = units;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Creates the organization in its country and publishes {@code OrganizationCreatedV1}; the caller checked the
     * rights. {@code COUNTRY_UNKNOWN} when the country is not loaded in the reference data (A8, M4).
     */
    @Override
    @Transactional
    public OrganizationId createOrganization(CreateOrganization command) {
        Instant now = clock.instant();
        if (reference.findCountry(command.countryCode()).isEmpty()) {
            throw new BusinessException("COUNTRY_UNKNOWN", ErrorCategory.RULE_VIOLATION, "Country not loaded");
        }
        Organization organization = organizations.save(Organization.create(command.type(), command.name(),
                command.countryCode(), command.contactPhone(), command.contactEmail(), now));
        events.publishEvent(new OrganizationCreatedV1(organization.id(), organization.type(), organization.name(),
                command.createdBy(), now));
        return organization.id();
    }

    /** One organization's public view. */
    @Override
    @Transactional(readOnly = true)
    public Optional<OrganizationSummary> findOrganization(OrganizationId organizationId) {
        return organizations.findById(organizationId).map(this::summary);
    }

    /** Several organizations' public views. */
    @Override
    @Transactional(readOnly = true)
    public List<OrganizationSummary> findOrganizations(Collection<OrganizationId> organizationIds) {
        return organizations.findAllById(organizationIds).stream().map(this::summary).toList();
    }

    /** One site's public view. */
    @Override
    @Transactional(readOnly = true)
    public Optional<SiteSummary> findSite(SiteId siteId) {
        return sites.findById(siteId).map(SitesApiService::summary);
    }

    /** Several sites' public views. */
    @Override
    @Transactional(readOnly = true)
    public List<SiteSummary> findSites(Collection<SiteId> siteIds) {
        return sites.findAllById(siteIds).stream().map(SitesApiService::summary).toList();
    }

    /** Active buildings of a site, ordered by name. */
    @Override
    @Transactional(readOnly = true)
    public List<BuildingSummary> activeBuildings(SiteId siteId) {
        return buildings.ofSite(siteId, false).stream().map(SitesApiService::summary).toList();
    }

    /** One building's public view. */
    @Override
    @Transactional(readOnly = true)
    public Optional<BuildingSummary> findBuilding(BuildingId buildingId) {
        return buildings.findById(buildingId).map(SitesApiService::summary);
    }

    /** One unit's public view. */
    @Override
    @Transactional(readOnly = true)
    public Optional<UnitSummary> findUnit(UnitId unitId) {
        return units.findById(unitId).map(SitesApiService::summary);
    }

    /** Several units' public views. */
    @Override
    @Transactional(readOnly = true)
    public List<UnitSummary> findUnits(Collection<UnitId> unitIds) {
        return units.findAllById(unitIds).stream().map(SitesApiService::summary).toList();
    }

    /** Back-office listing of every organization with its number of sites (ADR-035); the caller checks rights. */
    @Override
    @Transactional(readOnly = true)
    public PageResult<OrganizationAdminView> searchOrganizations(String namePart, Boolean active, String countryCode,
                                                                 PageQuery page) {
        PageResult<Organization> found = organizations.search(new OrganizationRepository.Search(namePart, active,
                countryCode), page);
        Map<OrganizationId, Long> counts = organizations.siteCounts(found.items().stream().map(Organization::id)
                .toList());
        return found.map(o -> adminView(o, counts.getOrDefault(o.id(), 0L)));
    }

    /**
     * Deactivates or reactivates an organization (C12, ADR-035); the caller checks rights. {@code NOT_FOUND}, {@code
     * ORGANIZATION_NOT_ACTIVE}, {@code ORGANIZATION_ALREADY_ACTIVE}. Publishes the matching event.
     */
    @Override
    @Transactional
    public OrganizationAdminView setOrganizationActive(OrganizationId organizationId, boolean active, String reason,
                                                       Actor actor) {
        Organization organization = organizations.findById(organizationId).orElseThrow(OrganizationService::notFound);
        Instant now = clock.instant();
        if (active) {
            organization.reactivate(now);
            events.publishEvent(new OrganizationReactivatedV1(organizationId, reason, actor, now));
        } else {
            organization.deactivate(reason, now);
            events.publishEvent(new OrganizationDeactivatedV1(organizationId, reason, actor, now));
        }
        organizations.save(organization);
        LOG.info("Organization {} {}", organizationId.value(), active ? "reactivated" : "deactivated");
        return adminView(organization, organizations.siteCounts(List.of(organizationId))
                .getOrDefault(organizationId, 0L));
    }

    /** Every unit of a site, active or archived. */
    @Override
    @Transactional(readOnly = true)
    public List<UnitSummary> unitsOfSite(SiteId siteId) {
        return units.ofSite(siteId).stream().map(SitesApiService::summary).toList();
    }

    /** Back-office view of an organization. */
    private OrganizationAdminView adminView(Organization o, long siteCount) {
        String currency = reference.findCountry(o.countryCode()).map(c -> c.currencyCode()).orElse(null);
        return new OrganizationAdminView(o.id(), o.type(), o.name(), o.countryCode(), currency,
                o.contactPhone().orElse(null), o.contactEmail().orElse(null), o.active(), o.version(), siteCount,
                o.createdAt(), o.deactivatedAt().orElse(null));
    }

    /** Public view of an organization, with the currency of its country (M5). */
    private OrganizationSummary summary(Organization o) {
        String currency = reference.findCountry(o.countryCode()).map(c -> c.currencyCode()).orElse(null);
        return new OrganizationSummary(o.id(), o.type(), o.name(), o.countryCode(), currency, o.active());
    }

    /** Public view of a site. */
    private static SiteSummary summary(Site s) {
        return new SiteSummary(s.id(), s.organizationId(), s.name(), s.active());
    }

    /** Public view of a building. */
    private static BuildingSummary summary(Building b) {
        return new BuildingSummary(b.id(), b.siteId(), b.name(), b.active());
    }

    /** Public view of a unit. */
    private static UnitSummary summary(Unit u) {
        return new UnitSummary(u.id(), u.organizationId(), u.siteId(), u.buildingId(), u.type(), u.reference(),
                u.active());
    }
}
