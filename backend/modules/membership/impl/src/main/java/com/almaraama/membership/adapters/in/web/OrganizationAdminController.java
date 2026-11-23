package com.almaraama.membership.adapters.in.web;

import com.almaraama.membership.adapters.in.web.MembershipWebDtos.AdminOrganizationResponse;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.Page;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.RequiredReasonRequest;
import com.almaraama.membership.application.OrganizationAdministration;
import com.almaraama.membership.application.OrganizationAdministration.AdminOrganization;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.OrganizationAdminView;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

/** Back-office of the client organizations (C8, C12, ADR-035). Creation stays {@code POST /v1/organizations}. */
@RestController
class OrganizationAdminController {

    private final OrganizationAdministration organizations;

    /** @param organizations the back-office use cases */
    OrganizationAdminController(OrganizationAdministration organizations) {
        this.organizations = organizations;
    }

    /** {@code GET /v1/admin/organizations}: needs {@code organization:read} on the platform. */
    @GetMapping("/v1/admin/organizations")
    Page<AdminOrganizationResponse> list(Perimeter perimeter, @RequestParam(required = false) String q,
                                         @RequestParam(required = false) Boolean active,
                                         @RequestParam(required = false) String countryCode,
                                         @RequestParam(required = false) Integer page,
                                         @RequestParam(required = false) Integer size) {
        return Page.of(organizations.search(perimeter, PeopleController.shorten(q), active, countryCode,
                PageQuery.of(page, size)), OrganizationAdminController::response);
    }

    /** {@code POST /v1/admin/organizations/{id}/deactivate}: needs {@code organization:deactivate}. */
    @PostMapping("/v1/admin/organizations/{organizationId}/deactivate")
    AdminOrganizationResponse deactivate(Perimeter perimeter, @PathVariable UUID organizationId,
                                         @Valid @RequestBody RequiredReasonRequest request) {
        return response(organizations.deactivate(perimeter, new OrganizationId(organizationId), request.reason()));
    }

    /** {@code POST /v1/admin/organizations/{id}/reactivate}: needs {@code organization:deactivate}. */
    @PostMapping("/v1/admin/organizations/{organizationId}/reactivate")
    AdminOrganizationResponse reactivate(Perimeter perimeter, @PathVariable UUID organizationId,
                                         @Valid @RequestBody RequiredReasonRequest request) {
        return response(organizations.reactivate(perimeter, new OrganizationId(organizationId), request.reason()));
    }

    /** Response body of an organization for the back-office. */
    private static AdminOrganizationResponse response(AdminOrganization a) {
        OrganizationAdminView o = a.organization();
        return new AdminOrganizationResponse(o.id().value(), o.type(), o.name(), o.countryCode(), o.currencyCode(),
                o.contactPhone(), o.contactEmail(), o.active(), o.version(), o.siteCount(), a.ownerCount(),
                o.createdAt(), o.deactivatedAt());
    }
}
