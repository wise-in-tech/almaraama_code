package com.almaraama.membership.application;

import com.almaraama.membership.domain.RoleAssignment;
import com.almaraama.membership.domain.RoleAssignmentRepository;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.OrganizationAdminView;
import com.almaraama.sites.api.SitesApi;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Back-office of the client organizations (C8, C12, ADR-035): every organization with its number of sites and of
 * promoteurs, deactivation and reactivation. Lives in {@code membership} because it needs the promoteurs (role
 * assignments) and the support sessions; the organization itself is changed through the sites module's API.
 */
@Service
public class OrganizationAdministration {

    /** An organization for the back-office with its number of active promoteurs (C8). */
    public record AdminOrganization(OrganizationAdminView organization, long ownerCount) {
    }

    private final SitesApi sites;
    private final RoleAssignmentRepository assignments;
    private final RoleCatalog catalog;
    private final SupportSessionService supportSessions;

    /** Wires the organizations, the role assignments and the support sessions. */
    OrganizationAdministration(SitesApi sites, RoleAssignmentRepository assignments, RoleCatalog catalog,
                               SupportSessionService supportSessions) {
        this.sites = sites;
        this.assignments = assignments;
        this.catalog = catalog;
        this.supportSessions = supportSessions;
    }

    /** Every organization by name, filtered; needs {@code organization:read} on the platform. */
    @Transactional(readOnly = true)
    public PageResult<AdminOrganization> search(Perimeter perimeter, String namePart, Boolean active,
                                                String countryCode, PageQuery page) {
        perimeter.requireOnPlatform(Permission.ORGANIZATION_READ);
        return sites.searchOrganizations(namePart, active, countryCode, page)
                .map(o -> new AdminOrganization(o, owners(o.id())));
    }

    /**
     * Deactivates an organization; its people lose access at their next request (nothing deleted, M2). Needs {@code
     * organization:deactivate}; {@code SUPPORT_SESSION_OPEN} while a support session is open on it, {@code
     * ORGANIZATION_NOT_ACTIVE}, {@code NOT_FOUND}.
     */
    @Transactional
    public AdminOrganization deactivate(Perimeter perimeter, OrganizationId organizationId, String reason) {
        perimeter.requireOnPlatform(Permission.ORGANIZATION_DEACTIVATE);
        if (supportSessions.openOn(organizationId)) {
            throw new BusinessException("SUPPORT_SESSION_OPEN", ErrorCategory.RULE_VIOLATION,
                    "End the support sessions first");
        }
        return new AdminOrganization(sites.setOrganizationActive(organizationId, false, reason, perimeter.actor()),
                owners(organizationId));
    }

    /** Gives an organization back its access; needs {@code organization:deactivate}; {@code ORGANIZATION_ALREADY_ACTIVE}. */
    @Transactional
    public AdminOrganization reactivate(Perimeter perimeter, OrganizationId organizationId, String reason) {
        perimeter.requireOnPlatform(Permission.ORGANIZATION_DEACTIVATE);
        return new AdminOrganization(sites.setOrganizationActive(organizationId, true, reason, perimeter.actor()),
                owners(organizationId));
    }

    /** Number of people holding a role that grants everything on the organization (its promoteurs, C8). */
    private long owners(OrganizationId organizationId) {
        return assignments.activeInOrganization(organizationId).stream()
                .filter(a -> a.scopeType() == ScopeType.ORGANIZATION)
                .map(RoleAssignment::role)
                .filter(catalog::grantsAll)
                .count();
    }
}
