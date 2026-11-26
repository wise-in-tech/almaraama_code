package com.almaraama.shared.perimeter;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.access.RoleCode;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;

/**
 * What an actor may see and do (M3, D5, ADR-026). Built for every request by the membership module from the
 * caller's roles, the role permissions of each organization, the person's adjustments, site accesses and units, and
 * passed as a parameter to use cases, never read from global state (CF-11).
 * <p>
 * Something outside the perimeter is reported as {@code NOT_FOUND}, so its existence is never revealed. Something
 * visible but without the needed permission is {@code PERMISSION_DENIED}. Business code checks permissions only,
 * never role codes (section 3.1); the roles are kept for display.
 *
 * @param platformRoles       roles on the platform itself (our team)
 * @param platformPermissions permissions on the platform ({@link Permission#ORGANIZATION_CREATE})
 * @param organizations       roles and effective permissions on each organization (valid for all its sites)
 * @param sites               per site: roles, effective permissions (organization ones included), access details
 */
public record Perimeter(Actor actor, Set<RoleCode> platformRoles, Set<Permission> platformPermissions,
                        Map<OrganizationId, OrganizationAccess> organizations, Map<SiteId, SiteAccess> sites) {

    /** Roles and effective permissions of the actor on one organization. */
    public record OrganizationAccess(Set<RoleCode> roles, Set<Permission> permissions) {
        public OrganizationAccess {
            roles = Set.copyOf(roles);
            permissions = Set.copyOf(permissions);
        }
    }

    /**
     * The actor's access to one site.
     *
     * @param member             {@code true} when the actor has an active site access (resident, C4)
     * @param allowedBuildingIds buildings the actor may see when restricted (C7); empty means every building
     * @param occupiedUnitIds    units the actor currently occupies (E1)
     */
    public record SiteAccess(OrganizationId organizationId, Set<RoleCode> roles, Set<Permission> permissions,
                             boolean member, Set<BuildingId> allowedBuildingIds, Set<UnitId> occupiedUnitIds) {
        public SiteAccess {
            Objects.requireNonNull(organizationId, "organizationId");
            roles = Set.copyOf(roles);
            permissions = Set.copyOf(permissions);
            allowedBuildingIds = Set.copyOf(allowedBuildingIds);
            occupiedUnitIds = Set.copyOf(occupiedUnitIds);
        }
    }

    public Perimeter {
        Objects.requireNonNull(actor, "actor");
        platformRoles = Set.copyOf(platformRoles);
        platformPermissions = Set.copyOf(platformPermissions);
        organizations = Map.copyOf(organizations);
        sites = Map.copyOf(sites);
    }

    /** A perimeter with no access at all: the actor only sees what belongs to them. */
    public static Perimeter of(Actor actor) {
        return new Perimeter(actor, Set.of(), Set.of(), Map.of(), Map.of());
    }

    // ------------------------------------------------------------------------------------------- visibility

    /** Whether the organization is visible: a role on it, or a role or access on one of its sites. */
    public boolean canSee(OrganizationId organizationId) {
        return organizations.containsKey(organizationId)
                || sites.values().stream().anyMatch(site -> site.organizationId().equals(organizationId));
    }

    /** A site is visible through a role on its organization, or through a role or an access on the site itself. */
    public boolean canSee(OrganizationId organizationId, SiteId siteId) {
        return organizations.containsKey(organizationId) || sites.containsKey(siteId);
    }

    /** Whether the actor sees the whole organization (a role on the organization itself), not only some sites. */
    public boolean seesWholeOrganization(OrganizationId organizationId) {
        return organizations.containsKey(organizationId);
    }

    /** {@code NOT_FOUND} when the organization is outside the perimeter (M3). */
    public void requireVisible(OrganizationId organizationId) {
        if (!canSee(organizationId)) {
            throw notFound();
        }
    }

    /** {@code NOT_FOUND} when the site is outside the perimeter (M3). */
    public void requireVisible(OrganizationId organizationId, SiteId siteId) {
        if (!canSee(organizationId, siteId)) {
            throw notFound();
        }
    }

    // ------------------------------------------------------------------------------------------- permissions

    /** Whether the actor holds the permission on the organization itself (effective, after adjustments). */
    public boolean can(Permission permission, OrganizationId organizationId) {
        OrganizationAccess access = organizations.get(organizationId);
        return access != null && access.permissions().contains(permission);
    }

    /** On a site: the site's effective permissions, which already include those coming from the organization. */
    public boolean can(Permission permission, OrganizationId organizationId, SiteId siteId) {
        SiteAccess site = sites.get(siteId);
        if (site != null && site.organizationId().equals(organizationId)) {
            return site.permissions().contains(permission);
        }
        return can(permission, organizationId);
    }

    /** {@code NOT_FOUND} outside the perimeter, {@code PERMISSION_DENIED} when visible without the permission. */
    public void require(Permission permission, OrganizationId organizationId) {
        requireVisible(organizationId);
        if (!can(permission, organizationId)) {
            throw denied();
        }
    }

    /**
     * {@code NOT_FOUND} when the site is outside the perimeter, {@code PERMISSION_DENIED} when visible without the
     * permission.
     */
    public void require(Permission permission, OrganizationId organizationId, SiteId siteId) {
        requireVisible(organizationId, siteId);
        if (!can(permission, organizationId, siteId)) {
            throw denied();
        }
    }

    /**
     * Whether the actor holds the permission on at least one organization or site: for what is not tied to one scope,
     * such as downloading an import template (ADR-027).
     */
    public boolean canAnywhere(Permission permission) {
        return organizations.values().stream().anyMatch(o -> o.permissions().contains(permission))
                || sites.values().stream().anyMatch(s -> s.permissions().contains(permission));
    }

    /** {@code PERMISSION_DENIED} unless the actor holds the permission on at least one organization or site. */
    public void requireAnywhere(Permission permission) {
        if (!canAnywhere(permission)) {
            throw denied();
        }
    }

    /**
     * Whether the actor holds the permission on the organization or on at least one of its sites: for what belongs to
     * the organization but may be completed by a site's manager (equipment families, suppliers, issue types, M7).
     */
    public boolean canInOrganization(Permission permission, OrganizationId organizationId) {
        return can(permission, organizationId) || sites.values().stream()
                .anyMatch(s -> s.organizationId().equals(organizationId) && s.permissions().contains(permission));
    }

    /**
     * {@code NOT_FOUND} when the organization is outside the perimeter, {@code PERMISSION_DENIED} unless the actor
     * holds the permission on it or on one of its sites (see {@link #canInOrganization}).
     */
    public void requireInOrganization(Permission permission, OrganizationId organizationId) {
        if (!canSee(organizationId)) {
            throw notFound();
        }
        if (!canInOrganization(permission, organizationId)) {
            throw denied();
        }
    }

    /** Whether the actor holds a platform permission, such as {@code organization:create}. */
    public boolean canOnPlatform(Permission permission) {
        return platformPermissions.contains(permission);
    }

    /** {@code PERMISSION_DENIED} when the actor lacks the platform permission. */
    public void requireOnPlatform(Permission permission) {
        if (!canOnPlatform(permission)) {
            throw denied();
        }
    }

    // ------------------------------------------------------------------------------------------- site details

    /** The actor's access to the site, when the site is in the perimeter through a site role or an access. */
    public Optional<SiteAccess> site(SiteId siteId) {
        return Optional.ofNullable(sites.get(siteId));
    }

    /**
     * Whether a building of a visible site is visible (C7). A role on the organization, or {@code unit:read} on the
     * site (the managers' permission), opens every building; a restricted resident sees only their allowed buildings.
     */
    public boolean canSeeBuilding(OrganizationId organizationId, SiteId siteId, BuildingId buildingId) {
        if (organizations.containsKey(organizationId)) {
            return true;
        }
        SiteAccess site = sites.get(siteId);
        if (site == null) {
            return false;
        }
        boolean restricted = !site.permissions().contains(Permission.UNIT_READ) && !site.allowedBuildingIds().isEmpty();
        return !restricted || site.allowedBuildingIds().contains(buildingId);
    }

    /** The answer for anything outside the perimeter: never reveals that it exists. */
    private static BusinessException notFound() {
        return new BusinessException("NOT_FOUND", ErrorCategory.NOT_FOUND, "Outside the caller's perimeter");
    }

    /** The answer for something visible that the actor may not act on. */
    private static BusinessException denied() {
        return new BusinessException("PERMISSION_DENIED", ErrorCategory.PERMISSION_DENIED, "Permission missing");
    }
}
