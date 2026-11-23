package com.almaraama.sites.api;

import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Public port of the sites module (CF-1). Other modules read organizations, sites, buildings and units here, by
 * typed ID (CF-2). These reads do not check any perimeter: the caller does, with its own rules.
 */
public interface SitesApi {

    /** Creates a client organization (A1). Used when our team onboards a client. */
    OrganizationId createOrganization(CreateOrganization command);

    /**
     * Back-office (ADR-035): one page of every organization by name, filtered by part of the name, state and country
     * (each optional). No perimeter check: the caller checks {@code organization:read} on the platform.
     */
    PageResult<OrganizationAdminView> searchOrganizations(String namePart, Boolean active, String countryCode,
                                                          PageQuery page);

    /**
     * C12, ADR-035: deactivates or reactivates an organization; nothing is deleted. No perimeter check: the caller
     * checks {@code organization:deactivate} on the platform. {@code NOT_FOUND}, {@code ORGANIZATION_NOT_ACTIVE},
     * {@code ORGANIZATION_ALREADY_ACTIVE}. Publishes {@code OrganizationDeactivatedV1} or {@code
     * OrganizationReactivatedV1}.
     */
    OrganizationAdminView setOrganizationActive(OrganizationId organizationId, boolean active, String reason,
                                                Actor actor);

    /** One organization's public view. */
    Optional<OrganizationSummary> findOrganization(OrganizationId organizationId);

    /** Several organizations' public views; unknown identifiers are left out. */
    List<OrganizationSummary> findOrganizations(Collection<OrganizationId> organizationIds);

    /** One site's public view, with its organization. */
    Optional<SiteSummary> findSite(SiteId siteId);

    /** Several sites' public views; unknown identifiers are left out. */
    List<SiteSummary> findSites(Collection<SiteId> siteIds);

    /** Active buildings of a site, ordered by name. */
    List<BuildingSummary> activeBuildings(SiteId siteId);

    /** One building's public view. */
    Optional<BuildingSummary> findBuilding(BuildingId buildingId);

    /** One unit's public view, with its site and organization. */
    Optional<UnitSummary> findUnit(UnitId unitId);

    /** Several units' public views; unknown identifiers are left out. */
    List<UnitSummary> findUnits(Collection<UnitId> unitIds);

    /** Every unit of a site, active or archived (imports match their references, D3). */
    List<UnitSummary> unitsOfSite(SiteId siteId);
}
