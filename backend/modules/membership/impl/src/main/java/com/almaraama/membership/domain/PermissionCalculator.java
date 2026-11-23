package com.almaraama.membership.domain;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.access.RoleCode;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.shared.perimeter.Perimeter.OrganizationAccess;
import com.almaraama.shared.perimeter.Perimeter.SiteAccess;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Computes what a person may see and do (ADR-026 revision 2), from data already loaded:
 * <pre>
 * role permissions(role, organization) = every permission of the organization   if the role grants all (C8)
 *                                      = defaults(role) + ADD - REMOVE           otherwise (C11 differences)
 * permissions(scope) = union of the role permissions of every active role covering the scope
 *                    + ADD adjustments of the person on the scope or its organization
 *                    - REMOVE adjustments of the person on the scope or its organization
 *                      (never removing what a grants-all role gives)
 * </pre>
 * A role on the organization makes every site visible; a site role or an active site access makes that site
 * visible. Roles are looked up by code only to read their permissions: no rule here depends on a role's name.
 */
public final class PermissionCalculator {

    /** The person's data needed to compute the perimeter. */
    public record Input(Actor actor, List<RoleAssignment> roles, List<PermissionAdjustment> adjustments,
                        List<SiteMembership> activeMemberships, List<Occupancy> activeOccupancies,
                        RolePermissions rolePermissions) {
    }

    /** The permissions of each role in each organization (defaults and differences), loaded once per request. */
    public interface RolePermissions {

        /** Whether the role grants every permission of the organization (the promoteur, C8). */
        boolean grantsAll(RoleCode role);

        /** The permissions of a role in an organization; for a platform role, its defaults. */
        Set<Permission> of(RoleCode role, Optional<OrganizationId> organizationId);
    }

    /** Pure computation only. */
    private PermissionCalculator() {
    }

    /**
     * The perimeter: platform permissions, organizations with an organization role, and sites with a role or an active
     * access, each with its effective permissions.
     */
    public static Perimeter perimeter(Input in) {
        Set<RoleCode> platformRoles = new HashSet<>();
        Set<Permission> platformPermissions = EnumSet.noneOf(Permission.class);
        Map<OrganizationId, Set<RoleCode>> organizationRoles = new HashMap<>();
        Map<SiteId, Set<RoleCode>> siteRoles = new HashMap<>();
        Map<SiteId, OrganizationId> siteOrganization = new HashMap<>();

        for (RoleAssignment assignment : in.roles()) {
            switch (assignment.scopeType()) {
                case PLATFORM -> {
                    platformRoles.add(assignment.role());
                    in.rolePermissions().of(assignment.role(), Optional.empty()).stream()
                            .filter(p -> p.appliesTo(ScopeType.PLATFORM)).forEach(platformPermissions::add);
                }
                case ORGANIZATION -> organizationRoles
                        .computeIfAbsent(assignment.organizationId().orElseThrow(), k -> new HashSet<>())
                        .add(assignment.role());
                case SITE -> {
                    SiteId site = assignment.siteId().orElseThrow();
                    siteRoles.computeIfAbsent(site, k -> new HashSet<>()).add(assignment.role());
                    siteOrganization.put(site, assignment.organizationId().orElseThrow());
                }
            }
        }
        in.activeMemberships().forEach(m -> siteOrganization.put(m.siteId(), m.organizationId()));

        Map<OrganizationId, OrganizationAccess> organizations = new HashMap<>();
        organizationRoles.forEach((organization, roles) -> organizations.put(organization,
                new OrganizationAccess(roles, permissions(in, organization, Optional.empty(), roles))));

        Map<SiteId, SiteAccess> sites = new HashMap<>();
        for (Map.Entry<SiteId, OrganizationId> entry : siteOrganization.entrySet()) {
            SiteId site = entry.getKey();
            OrganizationId organization = entry.getValue();
            Set<RoleCode> roles = new HashSet<>(organizationRoles.getOrDefault(organization, Set.of()));
            roles.addAll(siteRoles.getOrDefault(site, Set.of()));
            Optional<SiteMembership> membership = in.activeMemberships().stream()
                    .filter(m -> m.siteId().equals(site)).findFirst();
            Set<UnitId> units = new HashSet<>();
            in.activeOccupancies().stream().filter(o -> o.siteId().equals(site))
                    .forEach(o -> units.add(o.unitId()));
            sites.put(site, new SiteAccess(organization, roles,
                    permissions(in, organization, Optional.of(site), roles),
                    membership.isPresent(),
                    membership.map(SiteMembership::allowedBuildingIds).orElse(Set.<BuildingId>of()),
                    units));
        }
        return new Perimeter(in.actor(), platformRoles, platformPermissions, organizations, sites);
    }

    /**
     * C12, ADR-035: the perimeter of a platform administrator during a support session: on that organization and all
     * its sites, the permissions of a {@code grantsAll} role (read and write); everything else unchanged. The actor
     * carries the session, so every event and audit entry records it.
     */
    public static Perimeter withSupportSession(Perimeter base, OrganizationId organization, UUID sessionId) {
        Set<Permission> all = Permission.organizationWide();
        Map<OrganizationId, OrganizationAccess> organizations = new HashMap<>(base.organizations());
        Set<RoleCode> roles = Optional.ofNullable(base.organizations().get(organization))
                .map(OrganizationAccess::roles).orElse(Set.of());
        organizations.put(organization, new OrganizationAccess(roles, all));
        Map<SiteId, SiteAccess> sites = new HashMap<>();
        base.sites().forEach((site, access) -> sites.put(site, !access.organizationId().equals(organization) ? access
                : new SiteAccess(access.organizationId(), access.roles(), all, access.member(),
                access.allowedBuildingIds(), access.occupiedUnitIds())));
        return new Perimeter(base.actor().inSupportSession(sessionId), base.platformRoles(),
                base.platformPermissions(), organizations, sites);
    }

    /**
     * Effective permissions on the organization ({@code site} empty) or on one of its sites, from the roles covering
     * that scope, then the person's adjustments.
     */
    private static Set<Permission> permissions(Input in, OrganizationId organization, Optional<SiteId> site,
                                               Collection<RoleCode> roles) {
        ScopeType scope = site.isPresent() ? ScopeType.SITE : ScopeType.ORGANIZATION;
        Set<Permission> all = EnumSet.noneOf(Permission.class);
        Set<Permission> fromGrantsAll = EnumSet.noneOf(Permission.class);
        for (RoleCode role : roles) {
            if (in.rolePermissions().grantsAll(role)) {
                Permission.organizationWide().stream().filter(p -> p.appliesTo(scope)).forEach(fromGrantsAll::add);
            } else {
                in.rolePermissions().of(role, Optional.of(organization)).stream()
                        .filter(p -> p.appliesTo(scope)).forEach(all::add);
            }
        }
        adjust(in, organization, site, all, scope);
        all.addAll(fromGrantsAll);
        return all;
    }

    /**
     * ADD first, then REMOVE: a removal always wins. Organization adjustments also apply to its sites. Non-adjustable
     * permissions are never touched, even if an old row named one (C9).
     */
    private static void adjust(Input in, OrganizationId organization, Optional<SiteId> site,
                               Set<Permission> permissions, ScopeType scope) {
        List<PermissionAdjustment> relevant = in.adjustments().stream()
                .filter(a -> a.organizationId().equals(organization))
                .filter(a -> a.siteId().isEmpty() || a.siteId().equals(site))
                .filter(a -> a.permission() != null && a.permission().adjustable())
                .toList();
        relevant.stream().filter(a -> a.effect() == AdjustmentEffect.ADD)
                .map(PermissionAdjustment::permission).filter(p -> p.appliesTo(scope)).forEach(permissions::add);
        relevant.stream().filter(a -> a.effect() == AdjustmentEffect.REMOVE)
                .map(PermissionAdjustment::permission).forEach(permissions::remove);
    }
}
