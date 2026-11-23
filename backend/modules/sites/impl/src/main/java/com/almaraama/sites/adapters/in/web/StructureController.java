package com.almaraama.sites.adapters.in.web;

import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.BuildingInput;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.BuildingResponse;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.CountryResponse;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.OrganizationResponse;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.OutdoorAreaInput;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.OutdoorAreaResponse;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.Page;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.ProvisionalZoneRequest;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.SiteInput;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.SiteResponse;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.UnitCategoryInput;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.UnitCategoryResponse;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.UnitInput;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.UnitResponse;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.UpdateBuildingRequest;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.UpdateOrganizationRequest;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.UpdateOutdoorAreaRequest;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.UpdateSiteRequest;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.UpdateUnitCategoryRequest;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.UpdateUnitRequest;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.ZoneResponse;
import com.almaraama.sites.api.UnitType;
import com.almaraama.sites.application.BuildingService;
import com.almaraama.sites.application.BuildingService.BuildingCommand;
import com.almaraama.sites.application.OrganizationService;
import com.almaraama.sites.application.OrganizationService.UpdateOrganization;
import com.almaraama.sites.application.OutdoorAreaService;
import com.almaraama.sites.application.ReferenceService;
import com.almaraama.sites.application.SiteService;
import com.almaraama.sites.application.SiteService.CreateSite;
import com.almaraama.sites.application.SiteService.SiteFilter;
import com.almaraama.sites.application.SiteService.UpdateSite;
import com.almaraama.sites.application.UnitCategoryService;
import com.almaraama.sites.application.UnitCategoryService.CreateCategory;
import com.almaraama.sites.application.UnitService;
import com.almaraama.sites.domain.Organization;
import com.almaraama.sites.domain.UnitRepository;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Endpoints of the sites module (A1 to A9): reference data and zones, organizations, sites, buildings, outdoor areas,
 * units and unit categories. Every method receives the caller's {@link Perimeter} (CF-11); the use cases check it.
 */
@RestController
@Validated
class StructureController {

    private static final int MAX_FILTER = 50;
    private static final int MAX_ZONE_PAGE = 200;

    private final ReferenceService reference;
    private final OrganizationService organizations;
    private final SiteService sites;
    private final BuildingService buildings;
    private final OutdoorAreaService areas;
    private final UnitService units;
    private final UnitCategoryService categories;

    /** Wires the use cases of the sites module. */
    StructureController(ReferenceService reference, OrganizationService organizations, SiteService sites,
                        BuildingService buildings, OutdoorAreaService areas, UnitService units,
                        UnitCategoryService categories) {
        this.reference = reference;
        this.organizations = organizations;
        this.sites = sites;
        this.buildings = buildings;
        this.areas = areas;
        this.units = units;
        this.categories = categories;
    }

    // ---------------------------------------------------------------------------------------- reference data

    /** {@code GET /v1/reference/countries}: countries of the reference data with their parameters (A8). */
    @GetMapping("/v1/reference/countries")
    List<CountryResponse> countries(Perimeter perimeter) {
        return reference.countries().stream().map(CountryResponse::of).toList();
    }

    /**
     * {@code GET /v1/reference/countries/{code}/zones} (A8): level 1, the children of {@code parentId}, one level, or a
     * name search ({@code q}, 2 to 50 characters).
     */
    @GetMapping("/v1/reference/countries/{countryCode}/zones")
    Page<ZoneResponse> zones(Perimeter perimeter, @PathVariable String countryCode,
                             @RequestParam(required = false) UUID parentId,
                             @RequestParam(required = false) Integer level,
                             @RequestParam(required = false) String q,
                             @RequestParam(required = false) Integer page,
                             @RequestParam(required = false) Integer size) {
        String search = q == null || q.strip().length() < 2 ? null : cut(q);
        PageQuery query = PageQuery.of(page, size == null ? 100 : Math.min(size, MAX_ZONE_PAGE));
        return Page.of(reference.zones(perimeter, countryCode, parentId, level, search, query), ZoneResponse::of);
    }

    /** {@code GET /v1/reference/zones/{id}}: one zone with its ancestors. */
    @GetMapping("/v1/reference/zones/{zoneId}")
    ZoneResponse zone(Perimeter perimeter, @PathVariable UUID zoneId) {
        return ZoneResponse.of(reference.zone(perimeter, zoneId));
    }

    /** {@code POST /v1/zones/provisional}: needs {@code zone:create-provisional} (A8, criterion 3). */
    @PostMapping("/v1/zones/provisional")
    @ResponseStatus(HttpStatus.CREATED)
    ZoneResponse createProvisionalZone(Perimeter perimeter, @Valid @RequestBody ProvisionalZoneRequest request) {
        return ZoneResponse.of(reference.createProvisional(perimeter, new OrganizationId(request.organizationId()),
                Optional.ofNullable(request.parentId()), request.name().toName()));
    }

    // ----------------------------------------------------------------------------------------- organizations

    /** {@code GET /v1/organizations}: the organizations of the caller's perimeter, all of them for our team. */
    @GetMapping("/v1/organizations")
    Page<OrganizationResponse> organizations(Perimeter perimeter, @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size) {
        return Page.of(organizations.list(perimeter, PageQuery.of(page, size)), this::organization);
    }

    /** {@code GET /v1/organizations/{id}}; {@code NOT_FOUND} outside the perimeter. */
    @GetMapping("/v1/organizations/{organizationId}")
    OrganizationResponse organization(Perimeter perimeter, @PathVariable UUID organizationId) {
        return organization(organizations.get(perimeter, new OrganizationId(organizationId)));
    }

    /** {@code PUT /v1/organizations/{id}}: needs {@code organization:update}. */
    @PutMapping("/v1/organizations/{organizationId}")
    OrganizationResponse updateOrganization(Perimeter perimeter, @PathVariable UUID organizationId,
                                            @Valid @RequestBody UpdateOrganizationRequest request) {
        return organization(organizations.update(perimeter, new OrganizationId(organizationId),
                new UpdateOrganization(request.name(), request.contactPhone(), request.contactEmail(),
                        request.version())));
    }

    // ------------------------------------------------------------------------------------------------- sites

    /**
     * {@code GET /v1/organizations/{id}/sites} (criterion 3): all sites for a role on the organization, otherwise the
     * caller's sites; filtered by zone at any level, name and status.
     */
    @GetMapping("/v1/organizations/{organizationId}/sites")
    Page<SiteResponse> sites(Perimeter perimeter, @PathVariable UUID organizationId,
                             @RequestParam(required = false) UUID zoneId,
                             @RequestParam(required = false) String q,
                             @RequestParam(defaultValue = "false") boolean includeInactive,
                             @RequestParam(required = false) Integer page,
                             @RequestParam(required = false) Integer size) {
        return Page.of(sites.list(perimeter, new OrganizationId(organizationId),
                new SiteFilter(zoneId, cut(q), includeInactive), PageQuery.of(page, size)), SiteResponse::of);
    }

    /** {@code POST /v1/organizations/{id}/sites}: needs {@code site:create} (A1, A5, A9). */
    @PostMapping("/v1/organizations/{organizationId}/sites")
    @ResponseStatus(HttpStatus.CREATED)
    SiteResponse createSite(Perimeter perimeter, @PathVariable UUID organizationId,
                            @Valid @RequestBody SiteInput request) {
        return SiteResponse.of(sites.create(perimeter, new OrganizationId(organizationId), new CreateSite(
                request.type(), request.name(), request.address().toSpec(), request.lifecycleStatus())));
    }

    /** {@code GET /v1/sites/{id}}; {@code NOT_FOUND} outside the perimeter. */
    @GetMapping("/v1/sites/{siteId}")
    SiteResponse site(Perimeter perimeter, @PathVariable UUID siteId) {
        return SiteResponse.of(sites.get(perimeter, new SiteId(siteId)));
    }

    /** {@code PUT /v1/sites/{id}}: needs {@code site:update}, and {@code site:archive} to change {@code active}. */
    @PutMapping("/v1/sites/{siteId}")
    SiteResponse updateSite(Perimeter perimeter, @PathVariable UUID siteId,
                            @Valid @RequestBody UpdateSiteRequest request) {
        return SiteResponse.of(sites.update(perimeter, new SiteId(siteId), new UpdateSite(request.name(),
                request.address().toSpec(), request.lifecycleStatus(), request.active(), request.version())));
    }

    // --------------------------------------------------------------------------------------------- buildings

    /** {@code GET /v1/sites/{id}/buildings}: with their address; a restricted resident sees only theirs (C7). */
    @GetMapping("/v1/sites/{siteId}/buildings")
    List<BuildingResponse> buildings(Perimeter perimeter, @PathVariable UUID siteId,
                                     @RequestParam(defaultValue = "false") boolean includeInactive) {
        return buildings.list(perimeter, new SiteId(siteId), includeInactive).stream()
                .map(BuildingResponse::of).toList();
    }

    /**
     * {@code POST /v1/sites/{id}/buildings}: needs {@code building:create} (A2, A5); without address, a copy of the
     * site's (A9).
     */
    @PostMapping("/v1/sites/{siteId}/buildings")
    @ResponseStatus(HttpStatus.CREATED)
    BuildingResponse createBuilding(Perimeter perimeter, @PathVariable UUID siteId,
                                    @Valid @RequestBody BuildingInput request) {
        return BuildingResponse.of(buildings.create(perimeter, new SiteId(siteId), new BuildingCommand(
                request.name(), request.buildingUse(), request.floorCount(),
                Optional.ofNullable(request.address()).map(SitesWebDtos.AddressInput::toSpec))));
    }

    /** {@code PUT /v1/buildings/{id}}: needs {@code building:update}, and {@code building:archive} for {@code active}. */
    @PutMapping("/v1/buildings/{buildingId}")
    BuildingResponse updateBuilding(Perimeter perimeter, @PathVariable UUID buildingId,
                                    @Valid @RequestBody UpdateBuildingRequest request) {
        return BuildingResponse.of(buildings.update(perimeter, new BuildingId(buildingId), new BuildingCommand(
                        request.name(), request.buildingUse(), request.floorCount(),
                        Optional.of(request.address().toSpec())),
                request.active(), request.version()));
    }

    // ----------------------------------------------------------------------------------------- outdoor areas

    /** {@code GET /v1/sites/{id}/outdoor-areas} (A3). */
    @GetMapping("/v1/sites/{siteId}/outdoor-areas")
    List<OutdoorAreaResponse> outdoorAreas(Perimeter perimeter, @PathVariable UUID siteId,
                                           @RequestParam(defaultValue = "false") boolean includeInactive) {
        return areas.list(perimeter, new SiteId(siteId), includeInactive).stream()
                .map(OutdoorAreaResponse::of).toList();
    }

    /** {@code POST /v1/sites/{id}/outdoor-areas}: needs {@code outdoor-area:create}. */
    @PostMapping("/v1/sites/{siteId}/outdoor-areas")
    @ResponseStatus(HttpStatus.CREATED)
    OutdoorAreaResponse createOutdoorArea(Perimeter perimeter, @PathVariable UUID siteId,
                                          @Valid @RequestBody OutdoorAreaInput request) {
        return OutdoorAreaResponse.of(areas.create(perimeter, new SiteId(siteId), request.type(), request.name()));
    }

    /** {@code PUT /v1/outdoor-areas/{id}}: needs {@code outdoor-area:update}, and {@code :archive} for {@code active}. */
    @PutMapping("/v1/outdoor-areas/{outdoorAreaId}")
    OutdoorAreaResponse updateOutdoorArea(Perimeter perimeter, @PathVariable UUID outdoorAreaId,
                                          @Valid @RequestBody UpdateOutdoorAreaRequest request) {
        return OutdoorAreaResponse.of(areas.update(perimeter, outdoorAreaId, request.type(), request.name(),
                request.active(), request.version()));
    }

    // ------------------------------------------------------------------------------------------------- units

    /** {@code GET /v1/sites/{id}/units}: needs {@code unit:read}; by building, area, type, category, reference. */
    @GetMapping("/v1/sites/{siteId}/units")
    Page<UnitResponse> units(Perimeter perimeter, @PathVariable UUID siteId,
                             @RequestParam(required = false) UUID buildingId,
                             @RequestParam(required = false) UUID outdoorAreaId,
                             @RequestParam(required = false) UnitType type,
                             @RequestParam(required = false) UUID categoryId,
                             @RequestParam(required = false) String q,
                             @RequestParam(defaultValue = "false") boolean includeInactive,
                             @RequestParam(required = false) Integer page,
                             @RequestParam(required = false) Integer size) {
        UnitRepository.Filter filter = new UnitRepository.Filter(
                buildingId == null ? null : new BuildingId(buildingId), outdoorAreaId, type, categoryId, cut(q),
                includeInactive, Optional.empty());
        return Page.of(units.list(perimeter, new SiteId(siteId), filter, PageQuery.of(page, size)),
                UnitResponse::of);
    }

    /** {@code POST /v1/sites/{id}/units}: needs {@code unit:create} (A6, A7). */
    @PostMapping("/v1/sites/{siteId}/units")
    @ResponseStatus(HttpStatus.CREATED)
    UnitResponse createUnit(Perimeter perimeter, @PathVariable UUID siteId, @Valid @RequestBody UnitInput request) {
        return UnitResponse.of(units.create(perimeter, new SiteId(siteId), request.toSpec()));
    }

    /** {@code GET /v1/units/{id}}; {@code NOT_FOUND} outside the perimeter or the caller's buildings. */
    @GetMapping("/v1/units/{unitId}")
    UnitResponse unit(Perimeter perimeter, @PathVariable UUID unitId) {
        return UnitResponse.of(units.get(perimeter, new UnitId(unitId)));
    }

    /** {@code PUT /v1/units/{id}}: needs {@code unit:update}, and {@code unit:archive} to change {@code active}. */
    @PutMapping("/v1/units/{unitId}")
    UnitResponse updateUnit(Perimeter perimeter, @PathVariable UUID unitId,
                            @Valid @RequestBody UpdateUnitRequest request) {
        return UnitResponse.of(units.update(perimeter, new UnitId(unitId), request.toSpec(), request.active(),
                request.version()));
    }

    // -------------------------------------------------------------------------------------- unit categories

    /** {@code GET /v1/unit-categories}: needs {@code unit-category:read} on the site or the organization (A6). */
    @GetMapping("/v1/unit-categories")
    List<UnitCategoryResponse> unitCategories(Perimeter perimeter, @RequestParam(required = false) UUID siteId,
                                              @RequestParam(required = false) UUID organizationId,
                                              @RequestParam(required = false) UnitType unitType,
                                              @RequestParam(defaultValue = "false") boolean includeArchived) {
        return categories.list(perimeter, siteId, organizationId, unitType, includeArchived).stream()
                .map(UnitCategoryResponse::of).toList();
    }

    /** {@code POST /v1/unit-categories}: needs {@code unit-category:create} on the scope (A6, criterion 2). */
    @PostMapping("/v1/unit-categories")
    @ResponseStatus(HttpStatus.CREATED)
    UnitCategoryResponse createUnitCategory(Perimeter perimeter, @Valid @RequestBody UnitCategoryInput request) {
        return UnitCategoryResponse.of(categories.create(perimeter, new CreateCategory(request.scope().type(),
                request.scope().id(), request.unitType(), request.code(), request.name().toName(),
                request.sortOrder() == null ? 100 : request.sortOrder())));
    }

    /** {@code PUT /v1/unit-categories/{id}}: needs {@code unit-category:update}, and {@code :archive} for {@code active}. */
    @PutMapping("/v1/unit-categories/{categoryId}")
    UnitCategoryResponse updateUnitCategory(Perimeter perimeter, @PathVariable UUID categoryId,
                                            @Valid @RequestBody UpdateUnitCategoryRequest request) {
        return UnitCategoryResponse.of(categories.update(perimeter, categoryId, request.name().toName(),
                request.sortOrder(), request.active(), request.version()));
    }

    /** An organization with the currency of its country (M5). */
    private OrganizationResponse organization(Organization organization) {
        return OrganizationResponse.of(organization, reference.currencyOf(organization.countryCode()));
    }

    /** A free-text filter, trimmed and cut at 50 characters; {@code null} when blank. */
    private static String cut(String q) {
        if (q == null || q.isBlank()) {
            return null;
        }
        String stripped = q.strip();
        return stripped.length() <= MAX_FILTER ? stripped : stripped.substring(0, MAX_FILTER);
    }
}
