package com.almaraama.maintenance.adapters.in.web;

import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.CatalogCategory;
import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.CatalogType;
import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.IssueCategoryInput;
import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.IssueCategoryResponse;
import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.IssueTypeConfigInput;
import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.IssueTypeConfigResponse;
import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.IssueTypeInput;
import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.IssueTypeResponse;
import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.PersonRef;
import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.Text;
import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.UpdateIssueCategoryRequest;
import com.almaraama.maintenance.adapters.in.web.MaintenanceWebDtos.UpdateIssueTypeRequest;
import com.almaraama.maintenance.application.IssueTypeService;
import com.almaraama.maintenance.application.IssueTypeService.TypeCommand;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.perimeter.Perimeter;
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

/** Issue catalog, categories, types and their settings per site (contract tag {@code tickets}, H2 to H4, H8). */
@RestController
class IssueTypeController {

    private final IssueTypeService issueTypes;

    /** @param issueTypes issue type use cases */
    IssueTypeController(IssueTypeService issueTypes) {
        this.issueTypes = issueTypes;
    }

    /** {@code GET /v1/sites/{siteId}/issue-catalog}: see {@link IssueTypeService#catalog}. */
    @GetMapping("/v1/sites/{siteId}/issue-catalog")
    List<CatalogCategory> catalog(Perimeter perimeter, @PathVariable UUID siteId,
                                  @RequestParam(required = false) UUID assetId) {
        SiteId site = new SiteId(siteId);
        return issueTypes.catalog(perimeter, site, Optional.ofNullable(assetId)).stream()
                .map(c -> new CatalogCategory(c.category().id(), c.category().code(), Text.of(c.category().name()),
                        c.types().stream().map(t -> new CatalogType(t.id(), t.code(), Text.of(t.name()),
                                t.assetRequired(), issueTypes.visibilityOnSite(site, t))).toList()))
                .toList();
    }

    /** {@code GET /v1/organizations/{id}/issue-categories}: see {@link IssueTypeService#forOrganization}. */
    @GetMapping("/v1/organizations/{organizationId}/issue-categories")
    List<IssueCategoryResponse> categories(Perimeter perimeter, @PathVariable UUID organizationId,
                                           @RequestParam(defaultValue = "false") boolean includeArchived) {
        return issueTypes.forOrganization(perimeter, new OrganizationId(organizationId), includeArchived).stream()
                .map(IssueCategoryResponse::of).toList();
    }

    /** {@code POST /v1/organizations/{id}/issue-categories}: see {@link IssueTypeService#createCategory}. */
    @PostMapping("/v1/organizations/{organizationId}/issue-categories")
    @ResponseStatus(HttpStatus.CREATED)
    IssueCategoryResponse createCategory(Perimeter perimeter, @PathVariable UUID organizationId,
                                         @Valid @RequestBody IssueCategoryInput body) {
        return IssueCategoryResponse.of(issueTypes.createCategory(perimeter,
                Optional.of(new OrganizationId(organizationId)), body.code(), body.name().toText(),
                body.sortOrder() == null ? 100 : body.sortOrder()));
    }

    /** {@code GET /v1/admin/issue-categories}: see {@link IssueTypeService#platform}. */
    @GetMapping("/v1/admin/issue-categories")
    List<IssueCategoryResponse> platform(Perimeter perimeter,
                                         @RequestParam(defaultValue = "false") boolean includeArchived) {
        return issueTypes.platform(perimeter, includeArchived).stream().map(IssueCategoryResponse::of).toList();
    }

    /** {@code POST /v1/admin/issue-categories}: a platform default, see {@link IssueTypeService#createCategory}. */
    @PostMapping("/v1/admin/issue-categories")
    @ResponseStatus(HttpStatus.CREATED)
    IssueCategoryResponse createPlatformCategory(Perimeter perimeter, @Valid @RequestBody IssueCategoryInput body) {
        return IssueCategoryResponse.of(issueTypes.createCategory(perimeter, Optional.empty(), body.code(),
                body.name().toText(), body.sortOrder() == null ? 100 : body.sortOrder()));
    }

    /** {@code PUT /v1/issue-categories/{id}}: see {@link IssueTypeService#updateCategory}. */
    @PutMapping("/v1/issue-categories/{issueCategoryId}")
    IssueCategoryResponse updateCategory(Perimeter perimeter, @PathVariable UUID issueCategoryId,
                                         @Valid @RequestBody UpdateIssueCategoryRequest body) {
        return IssueCategoryResponse.of(issueTypes.updateCategory(perimeter, issueCategoryId, body.name().toText(),
                body.sortOrder(), body.active(), body.version()));
    }

    /** {@code POST /v1/issue-categories/{id}/types}: see {@link IssueTypeService#createType}. */
    @PostMapping("/v1/issue-categories/{issueCategoryId}/types")
    @ResponseStatus(HttpStatus.CREATED)
    IssueTypeResponse createType(Perimeter perimeter, @PathVariable UUID issueCategoryId,
                                 @RequestParam(required = false) UUID organizationId,
                                 @Valid @RequestBody IssueTypeInput body) {
        return IssueTypeResponse.of(issueTypes.createType(perimeter, issueCategoryId,
                Optional.ofNullable(organizationId).map(OrganizationId::new), new TypeCommand(body.code(),
                        body.name().toText(), body.assetRequired(), body.defaultVisibility(),
                        body.assetFamilyIds() == null ? List.of() : body.assetFamilyIds(),
                        body.sortOrder() == null ? 100 : body.sortOrder())));
    }

    /** {@code PUT /v1/issue-types/{id}}: see {@link IssueTypeService#updateType}. */
    @PutMapping("/v1/issue-types/{issueTypeId}")
    IssueTypeResponse updateType(Perimeter perimeter, @PathVariable UUID issueTypeId,
                                 @Valid @RequestBody UpdateIssueTypeRequest body) {
        return IssueTypeResponse.of(issueTypes.updateType(perimeter, issueTypeId, new TypeCommand(null,
                body.name().toText(), body.assetRequired(), body.defaultVisibility(), body.assetFamilyIds(),
                body.sortOrder()), body.active(), body.version()));
    }

    /** {@code GET /v1/sites/{siteId}/issue-type-configs}: see {@link IssueTypeService#configs}. */
    @GetMapping("/v1/sites/{siteId}/issue-type-configs")
    List<IssueTypeConfigResponse> configs(Perimeter perimeter, @PathVariable UUID siteId) {
        return issueTypes.configs(perimeter, new SiteId(siteId)).stream().map(IssueTypeConfigResponse::of).toList();
    }

    /** {@code PUT /v1/sites/{siteId}/issue-type-configs/{issueTypeId}}: see {@link IssueTypeService#setConfig}. */
    @PutMapping("/v1/sites/{siteId}/issue-type-configs/{issueTypeId}")
    IssueTypeConfigResponse setConfig(Perimeter perimeter, @PathVariable UUID siteId, @PathVariable UUID issueTypeId,
                                      @Valid @RequestBody IssueTypeConfigInput body) {
        return IssueTypeConfigResponse.of(issueTypes.setConfig(perimeter, new SiteId(siteId), issueTypeId,
                body.routing(), body.technicianIds() == null ? List.of() : body.technicianIds(), body.visibility(),
                body.active(), Optional.ofNullable(body.version())));
    }

    /** {@code GET /v1/sites/{siteId}/eligible-technicians}: see {@link IssueTypeService#eligibleTechnicians}. */
    @GetMapping("/v1/sites/{siteId}/eligible-technicians")
    List<PersonRef> eligible(Perimeter perimeter, @PathVariable UUID siteId) {
        return issueTypes.eligibleTechnicians(perimeter, new SiteId(siteId)).stream().map(PersonRef::of).toList();
    }
}
