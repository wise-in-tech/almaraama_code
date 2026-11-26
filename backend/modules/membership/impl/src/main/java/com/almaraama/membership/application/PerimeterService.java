package com.almaraama.membership.application;

import com.almaraama.membership.api.PerimeterApi;
import com.almaraama.membership.domain.MembershipStatus;
import com.almaraama.membership.domain.Occupancy;
import com.almaraama.membership.domain.OccupancyRepository;
import com.almaraama.membership.domain.PermissionAdjustment;
import com.almaraama.membership.domain.PermissionAdjustmentRepository;
import com.almaraama.membership.domain.PermissionCalculator;
import com.almaraama.membership.domain.RoleAssignment;
import com.almaraama.membership.domain.RoleAssignmentRepository;
import com.almaraama.membership.domain.SiteMembership;
import com.almaraama.membership.domain.SiteMembershipRepository;
import com.almaraama.membership.domain.SupportSessionRepository;
import com.almaraama.membership.domain.ValidationStatus;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.SitesApi;
import java.time.Clock;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Builds a person's {@link Perimeter} for one request (CF-11, RB-4): a few indexed reads, then a pure computation
 * ({@link PermissionCalculator}). Nothing is cached between requests, so a change of role or permission applies
 * on the very next request. A deactivated organization is left out entirely (ADR-035); an open support session of a
 * platform administrator adds its organization with the promoteur's permissions (C12).
 */
@Service
class PerimeterService implements PerimeterApi {

    private final RoleAssignmentRepository assignments;
    private final PermissionAdjustmentRepository adjustments;
    private final SiteMembershipRepository memberships;
    private final OccupancyRepository occupancies;
    private final RolePermissionsSource rolePermissions;
    private final SupportSessionRepository supportSessions;
    private final SitesApi sites;
    private final Clock clock;

    /** Wires the reads the perimeter is computed from. */
    PerimeterService(RoleAssignmentRepository assignments, PermissionAdjustmentRepository adjustments,
                     SiteMembershipRepository memberships, OccupancyRepository occupancies,
                     RolePermissionsSource rolePermissions, SupportSessionRepository supportSessions, SitesApi sites,
                     Clock clock) {
        this.assignments = assignments;
        this.adjustments = adjustments;
        this.memberships = memberships;
        this.occupancies = occupancies;
        this.rolePermissions = rolePermissions;
        this.supportSessions = supportSessions;
        this.sites = sites;
        this.clock = clock;
    }

    /**
     * Reads the person's roles, adjustments, active accesses and validated units, leaves out deactivated
     * organizations, computes the perimeter, then adds an open support session.
     */
    @Override
    @Transactional(readOnly = true)
    public Perimeter perimeterOf(PersonId personId) {
        Set<OrganizationId> inactive = new HashSet<>();
        List<RoleAssignment> allRoles = assignments.activeOfPerson(personId);
        List<SiteMembership> allActive = memberships.ofPerson(personId).stream()
                .filter(m -> m.status() == MembershipStatus.ACTIVE).toList();
        Set<OrganizationId> organizations = new HashSet<>();
        allRoles.forEach(r -> r.organizationId().ifPresent(organizations::add));
        allActive.forEach(m -> organizations.add(m.organizationId()));
        if (!organizations.isEmpty()) {
            sites.findOrganizations(organizations).stream().filter(o -> !o.active())
                    .forEach(o -> inactive.add(o.id()));
            organizations.removeAll(inactive);
        }

        List<RoleAssignment> roles = allRoles.stream()
                .filter(r -> r.organizationId().map(o -> !inactive.contains(o)).orElse(true)).toList();
        List<SiteMembership> active = allActive.stream().filter(m -> !inactive.contains(m.organizationId())).toList();
        List<PermissionAdjustment> personAdjustments = adjustments.activeOfPerson(personId).stream()
                .filter(a -> !inactive.contains(a.organizationId())).toList();
        List<Occupancy> units = occupancies.activeOfPerson(personId).stream()
                .filter(o -> o.validationStatus() == ValidationStatus.VALIDATED)
                .filter(o -> !inactive.contains(o.organizationId())).toList();

        Perimeter perimeter = PermissionCalculator.perimeter(new PermissionCalculator.Input(Actor.person(personId),
                roles, personAdjustments, active, units, rolePermissions.forOrganizations(organizations)));
        if (!perimeter.canOnPlatform(Permission.SUPPORT_ACCESS)) {
            return perimeter;
        }
        return supportSessions.openOf(personId, clock.instant())
                .filter(session -> sites.findOrganization(session.organizationId()).map(o -> o.active())
                        .orElse(false))
                .map(session -> PermissionCalculator.withSupportSession(perimeter, session.organizationId(),
                        session.id()))
                .orElse(perimeter);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<PersonId> holders(Permission permission, OrganizationId organizationId,
                                  com.almaraama.shared.id.SiteId siteId, boolean siteRolesFirst) {
        List<RoleAssignment> active = assignments.activeInOrganization(organizationId);
        java.util.function.Function<List<RoleAssignment>, List<PersonId>> holding = list -> list.stream()
                .map(RoleAssignment::personId).distinct()
                .filter(person -> perimeterOf(person).can(permission, organizationId, siteId)).toList();
        List<RoleAssignment> onSite = active.stream()
                .filter(a -> a.siteId().equals(java.util.Optional.of(siteId))).toList();
        if (!siteRolesFirst) {
            return holding.apply(active.stream().filter(a -> a.scopeType() == com.almaraama.shared.access.ScopeType.ORGANIZATION
                    || a.siteId().equals(java.util.Optional.of(siteId))).toList());
        }
        List<PersonId> site = holding.apply(onSite);
        return site.isEmpty() ? holding.apply(active.stream()
                .filter(a -> a.scopeType() == com.almaraama.shared.access.ScopeType.ORGANIZATION).toList()) : site;
    }
}
