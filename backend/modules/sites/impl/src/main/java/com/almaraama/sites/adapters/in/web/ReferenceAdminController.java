package com.almaraama.sites.adapters.in.web;

import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.AdminZoneResponse;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.MergeZoneRequest;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.Page;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.PlatformUnitCategoryInput;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.UnitCategoryResponse;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.UpdateUnitCategoryRequest;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.UpdateZoneRequest;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.ValidateZoneRequest;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.VersionedReasonRequest;
import com.almaraama.sites.adapters.in.web.SitesWebDtos.ZoneInput;
import com.almaraama.sites.api.UnitType;
import com.almaraama.sites.application.UnitCategoryService;
import com.almaraama.sites.application.ZoneAdministration;
import com.almaraama.sites.domain.ZoneRepository;
import com.almaraama.sites.domain.ZoneStatus;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Back-office of the reference data (A6, A8, M7, C12, ADR-035): zones of every status and the platform's default unit
 * categories. Platform permissions only ({@code reference-data:*}); never an organization's data.
 */
@RestController
class ReferenceAdminController {

    private final ZoneAdministration zones;
    private final UnitCategoryService categories;

    /** Wires the zone back-office and the categories. */
    ReferenceAdminController(ZoneAdministration zones, UnitCategoryService categories) {
        this.zones = zones;
        this.categories = categories;
    }

    /** {@code GET /v1/admin/zones}: zones of a country in every status; needs {@code reference-data:read}. */
    @GetMapping("/v1/admin/zones")
    Page<AdminZoneResponse> zones(Perimeter perimeter, @RequestParam String countryCode,
                                  @RequestParam(required = false) ZoneStatus status,
                                  @RequestParam(required = false) UUID parentId,
                                  @RequestParam(required = false) String q,
                                  @RequestParam(required = false) Integer page,
                                  @RequestParam(required = false) Integer size) {
        String search = q == null || q.strip().length() < 2 ? null : q.strip().substring(0, Math.min(50, q.strip().length()));
        return Page.of(zones.search(perimeter, new ZoneRepository.AdminFilter(countryCode, status, parentId, search),
                PageQuery.of(page, size)), AdminZoneResponse::of);
    }

    /** {@code POST /v1/admin/zones}: a validated zone; needs {@code reference-data:create}. */
    @PostMapping("/v1/admin/zones")
    @ResponseStatus(HttpStatus.CREATED)
    AdminZoneResponse create(Perimeter perimeter, @Valid @RequestBody ZoneInput request) {
        return AdminZoneResponse.of(zones.create(perimeter, request.countryCode(),
                Optional.ofNullable(request.parentId()), request.code(), request.name().toName()));
    }

    /** {@code PUT /v1/admin/zones/{id}}: corrects code and names; needs {@code reference-data:update}. */
    @PutMapping("/v1/admin/zones/{zoneId}")
    AdminZoneResponse update(Perimeter perimeter, @PathVariable UUID zoneId,
                             @Valid @RequestBody UpdateZoneRequest request) {
        return AdminZoneResponse.of(zones.correct(perimeter, zoneId, request.code(), request.name().toName(),
                request.version()));
    }

    /** {@code POST /v1/admin/zones/{id}/validate}: needs {@code reference-data:validate-provisional} (A8). */
    @PostMapping("/v1/admin/zones/{zoneId}/validate")
    AdminZoneResponse validate(Perimeter perimeter, @PathVariable UUID zoneId,
                               @Valid @RequestBody ValidateZoneRequest request) {
        return AdminZoneResponse.of(zones.validate(perimeter, zoneId, request.code(),
                Optional.ofNullable(request.parentId()),
                Optional.ofNullable(request.name()).map(SitesWebDtos.LocalizedTextInput::toName), request.version()));
    }

    /** {@code POST /v1/admin/zones/{id}/merge}: needs {@code reference-data:validate-provisional} (A8). */
    @PostMapping("/v1/admin/zones/{zoneId}/merge")
    AdminZoneResponse merge(Perimeter perimeter, @PathVariable UUID zoneId,
                            @Valid @RequestBody MergeZoneRequest request) {
        return AdminZoneResponse.of(zones.merge(perimeter, zoneId, request.targetZoneId(), request.version()));
    }

    /** {@code POST /v1/admin/zones/{id}/deactivate}: needs {@code reference-data:archive} (M2). */
    @PostMapping("/v1/admin/zones/{zoneId}/deactivate")
    AdminZoneResponse deactivate(Perimeter perimeter, @PathVariable UUID zoneId,
                                 @Valid @RequestBody VersionedReasonRequest request) {
        return AdminZoneResponse.of(zones.deactivate(perimeter, zoneId, request.version()));
    }

    /** {@code GET /v1/admin/unit-categories}: the platform defaults; needs {@code reference-data:read}. */
    @GetMapping("/v1/admin/unit-categories")
    List<UnitCategoryResponse> categories(Perimeter perimeter, @RequestParam(required = false) UnitType unitType,
                                          @RequestParam(defaultValue = "false") boolean includeArchived) {
        return categories.platformDefaults(perimeter, unitType, includeArchived).stream()
                .map(UnitCategoryResponse::of).toList();
    }

    /** {@code POST /v1/admin/unit-categories}: a platform default; needs {@code reference-data:create} (A6, M7). */
    @PostMapping("/v1/admin/unit-categories")
    @ResponseStatus(HttpStatus.CREATED)
    UnitCategoryResponse createCategory(Perimeter perimeter, @Valid @RequestBody PlatformUnitCategoryInput request) {
        return UnitCategoryResponse.of(categories.createPlatformDefault(perimeter, request.unitType(), request.code(),
                request.name().toName(), request.sortOrder() == null ? 100 : request.sortOrder()));
    }

    /** {@code PUT /v1/admin/unit-categories/{id}}: needs {@code reference-data:update} (and {@code :archive}). */
    @PutMapping("/v1/admin/unit-categories/{categoryId}")
    UnitCategoryResponse updateCategory(Perimeter perimeter, @PathVariable UUID categoryId,
                                        @Valid @RequestBody UpdateUnitCategoryRequest request) {
        return UnitCategoryResponse.of(categories.updatePlatformDefault(perimeter, categoryId,
                request.name().toName(), request.sortOrder(), request.active(), request.version()));
    }
}
