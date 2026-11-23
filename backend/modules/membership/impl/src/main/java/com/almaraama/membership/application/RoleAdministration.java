package com.almaraama.membership.application;

import com.almaraama.membership.api.events.OrganizationRolePermissionsChangedV1;
import com.almaraama.membership.api.events.PermissionAdjustedV1;
import com.almaraama.membership.api.events.PermissionAdjustmentCancelledV1;
import com.almaraama.membership.api.events.RoleAssignedV1;
import com.almaraama.membership.api.events.RoleAssignmentEndedV1;
import com.almaraama.membership.domain.AdjustmentEffect;
import com.almaraama.membership.domain.OrganizationRoleDelta;
import com.almaraama.membership.domain.PermissionAdjustment;
import com.almaraama.membership.domain.PermissionAdjustmentRepository;
import com.almaraama.membership.domain.Role;
import com.almaraama.membership.domain.RoleAssignment;
import com.almaraama.membership.domain.RoleAssignmentRepository;
import com.almaraama.membership.domain.RoleKind;
import com.almaraama.membership.domain.RoleRepository;
import com.almaraama.membership.domain.SiteMembershipRepository;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.access.RoleCode;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.SitesApi;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Roles and permissions of an organization (C1, C8 to C11, ADR-026 revision 2). Every check is a permission, never a
 * role name (section 3.1):
 * <ul>
 *   <li>RB-1: giving a role needs {@code role-assignment:grant}, except roles of kind {@code OCCUPANT}, which {@code
 *   account:invite} or {@code access:approve} may give; changing permissions needs {@code
 *   permission-adjustment:manage}. Both are non-adjustable, so only a {@code grantsAll} role holds them (C9).</li>
 *   <li>RB-2: the last assignment of a {@code grantsAll} role cannot end (C8).</li>
 *   <li>RB-3: roles and permissions only on the scopes they declare.</li>
 *   <li>RB-5: every change is published for the audit log (C10).</li>
 *   <li>C11: an organization stores only its differences from the defaults.</li>
 * </ul>
 */
@Service
public class RoleAdministration {

    private static final Logger LOG = LoggerFactory.getLogger(RoleAdministration.class);

    /** A role's permissions in an organization: defaults, active differences and the result. */
    public record OrganizationRoleView(Role role, Set<Permission> defaults, List<OrganizationRoleDelta> deltas,
                                       Set<Permission> effective, boolean customized, boolean editable) {
    }

    /** Where an effective permission of a person comes from. */
    public enum Origin {
        ROLE_DEFAULT, ROLE_DELTA, ALL, PERSON_ADDED, PERSON_REMOVED
    }

    /** One effective (or removed) permission with its origin. */
    public record Granted(Permission permission, Origin origin, Optional<RoleCode> role,
                          Optional<UUID> adjustmentId) {
    }

    /** Effective permissions of a person on one scope, and what was removed for them. */
    public record ScopePermissions(ScopeType scopeType, UUID scopeId, Set<RoleCode> roles, List<Granted> granted,
                                   List<Granted> removed) {
    }

    private final RoleCatalog catalog;
    private final RoleRepository roles;
    private final RoleAssignmentRepository assignments;
    private final PermissionAdjustmentRepository adjustments;
    private final SiteMembershipRepository memberships;
    private final RolePermissionsSource rolePermissions;
    private final SitesApi sites;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the ports and collaborators of role and permission management. */
    RoleAdministration(RoleCatalog catalog, RoleRepository roles, RoleAssignmentRepository assignments,
                       PermissionAdjustmentRepository adjustments, SiteMembershipRepository memberships,
                       RolePermissionsSource rolePermissions, SitesApi sites, ApplicationEventPublisher events,
                       Clock clock) {
        this.catalog = catalog;
        this.roles = roles;
        this.assignments = assignments;
        this.adjustments = adjustments;
        this.memberships = memberships;
        this.rolePermissions = rolePermissions;
        this.sites = sites;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------- role catalogue (C1)

    /** Every role with its default permissions (specification section 3.3), platform roles included. */
    @Transactional(readOnly = true)
    public List<Role> catalogue() {
        return catalog.all();
    }

    /** The default permissions of a role. */
    public Set<Permission> defaults(RoleCode role) {
        return catalog.defaults(role);
    }

    // -------------------------------------------------------------------------- organization differences (C11)

    /** Each organization role with its defaults, differences and effective permissions; needs {@code permission-adjustment:manage}. */
    @Transactional(readOnly = true)
    public List<OrganizationRoleView> organizationRoles(Perimeter perimeter, OrganizationId organization) {
        perimeter.require(Permission.PERMISSION_ADJUSTMENT_MANAGE, organization);
        List<OrganizationRoleDelta> deltas = roles.activeDeltas(List.of(organization));
        return catalog.all().stream().filter(r -> r.kind() != RoleKind.PLATFORM)
                .map(role -> view(organization, role, deltas)).toList();
    }

    /**
     * Sets what a role can do in the organization (C9, C11); needs {@code permission-adjustment:manage}. The wanted
     * list is compared with the defaults: only the differences are stored, the ones no longer wanted are cancelled.
     * {@code ROLE_NOT_EDITABLE}, {@code PERMISSION_NOT_ADJUSTABLE}, {@code PERMISSION_SCOPE_INVALID}. Publishes {@code
     * OrganizationRolePermissionsChangedV1}.
     */
    @Transactional
    public OrganizationRoleView setRolePermissions(Perimeter perimeter, OrganizationId organization, RoleCode code,
                                                   Set<Permission> wanted, String reason) {
        perimeter.require(Permission.PERMISSION_ADJUSTMENT_MANAGE, organization);
        Role role = requireEditable(code);
        Set<Permission> defaults = catalog.defaults(code);
        for (Permission permission : wanted) {
            if (!defaults.contains(permission) && !permission.adjustable()) {
                throw rule("PERMISSION_NOT_ADJUSTABLE");
            }
            if (role.scopes().stream().noneMatch(permission::appliesTo)) {
                throw rule("PERMISSION_SCOPE_INVALID");
            }
        }
        PersonId by = actor(perimeter);
        Instant now = clock.instant();
        Set<Permission> before = rolePermissions.of(code, organization);
        Map<Permission, AdjustmentEffect> desired = new LinkedHashMap<>();
        wanted.stream().filter(p -> !defaults.contains(p)).forEach(p -> desired.put(p, AdjustmentEffect.ADD));
        defaults.stream().filter(p -> !wanted.contains(p) && p.adjustable())
                .forEach(p -> desired.put(p, AdjustmentEffect.REMOVE));

        for (OrganizationRoleDelta delta : deltasOf(organization, code)) {
            boolean keep = delta.permission().map(p -> desired.get(p) == delta.effect()).orElse(false);
            if (keep) {
                desired.remove(delta.permission().get());
            } else {
                delta.cancel(by, now);
                roles.save(delta);
            }
        }
        desired.forEach((permission, effect) -> roles.save(
                OrganizationRoleDelta.of(organization, code, permission, effect, reason, by, now)));
        Set<Permission> after = rolePermissions.of(code, organization);
        events.publishEvent(new OrganizationRolePermissionsChangedV1(organization, code, before, after,
                perimeter.actor(), now));
        LOG.debug("Permissions of role {} in organization {}: {} differences", code, organization.value(),
                deltasOf(organization, code).size());
        return view(organization, role, roles.activeDeltas(List.of(organization)));
    }

    /**
     * Puts a role back on the defaults (section 3.6): cancels every active difference; needs {@code
     * permission-adjustment:manage}; {@code ROLE_NOT_EDITABLE}. Publishes {@code OrganizationRolePermissionsChangedV1}.
     */
    @Transactional
    public OrganizationRoleView resetRole(Perimeter perimeter, OrganizationId organization, RoleCode code) {
        perimeter.require(Permission.PERMISSION_ADJUSTMENT_MANAGE, organization);
        Role role = requireEditable(code);
        PersonId by = actor(perimeter);
        Instant now = clock.instant();
        Set<Permission> before = rolePermissions.of(code, organization);
        deltasOf(organization, code).forEach(delta -> {
            delta.cancel(by, now);
            roles.save(delta);
        });
        events.publishEvent(new OrganizationRolePermissionsChangedV1(organization, code, before,
                catalog.defaults(code), perimeter.actor(), now));
        return view(organization, role, roles.activeDeltas(List.of(organization)));
    }

    /** A role's defaults, differences and effective permissions in the organization; every permission when it grants all. */
    private OrganizationRoleView view(OrganizationId organization, Role role, List<OrganizationRoleDelta> all) {
        if (role.grantsAll()) {
            return new OrganizationRoleView(role, Set.of(), List.of(), Permission.organizationWide(), false, false);
        }
        List<OrganizationRoleDelta> mine = all.stream().filter(d -> d.roleCode().equals(role.code())).toList();
        return new OrganizationRoleView(role, catalog.defaults(role.code()), mine,
                rolePermissions.of(role.code(), organization), !mine.isEmpty(), role.editable());
    }

    /** The active differences of one role in one organization. */
    private List<OrganizationRoleDelta> deltasOf(OrganizationId organization, RoleCode code) {
        return roles.activeDeltas(List.of(organization)).stream().filter(d -> d.roleCode().equals(code)).toList();
    }

    /** {@code ROLE_UNKNOWN}, or {@code ROLE_NOT_EDITABLE} for a {@code grantsAll} or platform role. */
    private Role requireEditable(RoleCode code) {
        Role role = catalog.require(code);
        if (!role.editable()) {
            throw rule("ROLE_NOT_EDITABLE");
        }
        return role;
    }

    // ------------------------------------------------------------------------------------ role assignments

    /** Role assignments of the organization; needs {@code account:read}, on the filtered site when given. */
    @Transactional(readOnly = true)
    public PageResult<RoleAssignment> assignments(Perimeter perimeter, OrganizationId organization,
                                                  RoleAssignmentRepository.Filter filter, PageQuery page) {
        if (filter.siteId() != null) {
            perimeter.require(Permission.ACCOUNT_READ, organization, filter.siteId());
        } else {
            perimeter.require(Permission.ACCOUNT_READ, organization);
        }
        return assignments.search(organization, filter, page);
    }

    /**
     * Gives a role to someone the organization already knows (C1, C2, C3, C8); RB-1 and RB-3; {@code
     * ROLE_NOT_GRANTABLE}, {@code ROLE_UNKNOWN}, {@code ROLE_SCOPE_INVALID}, {@code ROLE_ASSIGNMENT_EXISTS}.
     */
    @Transactional
    public RoleAssignment assign(Perimeter perimeter, OrganizationId organization, PersonId person, RoleCode code,
                                 ScopeType scope, UUID scopeId) {
        Role role = catalog.require(code);
        Optional<SiteId> site = scope == ScopeType.SITE ? Optional.of(new SiteId(requireScopeId(scopeId)))
                : Optional.empty();
        requireCanGrant(perimeter, organization, site, role);
        requireKnownToOrganization(person, organization);
        return give(perimeter, organization, person, role, scope, scopeId, clock.instant());
    }

    /**
     * RB-1: {@code role-assignment:grant} on the scope gives any role of the organization; without it, {@code
     * account:invite} or {@code access:approve} give only roles of kind {@code OCCUPANT}. Platform roles are never
     * given through the API. {@code NOT_FOUND} outside the perimeter, {@code ROLE_NOT_GRANTABLE} (403) otherwise.
     */
    void requireCanGrant(Perimeter perimeter, OrganizationId organization, Optional<SiteId> site, Role role) {
        if (site.isPresent()) {
            perimeter.requireVisible(organization, site.get());
        } else {
            perimeter.requireVisible(organization);
        }
        boolean granted = role.kind() != RoleKind.PLATFORM && role.organizationId().map(organization::equals)
                .orElse(true) && (can(perimeter, Permission.ROLE_ASSIGNMENT_GRANT, organization, site)
                || role.kind() == RoleKind.OCCUPANT && (can(perimeter, Permission.ACCOUNT_INVITE, organization, site)
                || can(perimeter, Permission.ACCESS_APPROVE, organization, site)));
        LOG.debug("Grant of role {} on {} by {}: {}", role.code(), site.map(s -> "site " + s.value())
                .orElse("organization " + organization.value()), perimeter.actor().id(), granted);
        if (!granted) {
            throw new BusinessException("ROLE_NOT_GRANTABLE", ErrorCategory.PERMISSION_DENIED,
                    "The caller cannot give this role (RB-1)");
        }
    }

    /** Gives a role after the caller's rights were checked; shared with invitations, approvals and onboarding. */
    RoleAssignment give(Perimeter perimeter, OrganizationId organization, PersonId person, Role role,
                        ScopeType scope, UUID scopeId, Instant now) {
        SiteId site = null;
        if (scope == ScopeType.SITE) {
            site = new SiteId(requireScopeId(scopeId));
            requireSiteOf(organization, site);
        } else if (scope == ScopeType.ORGANIZATION && scopeId != null && !organization.value().equals(scopeId)) {
            throw notFound();
        } else if (scope == ScopeType.PLATFORM) {
            throw rule("ROLE_SCOPE_INVALID");
        }
        if (assignments.active(person, role.code(), scope, organization, site).isPresent()) {
            throw new BusinessException("ROLE_ASSIGNMENT_EXISTS", ErrorCategory.CONFLICT, "Already holds it");
        }
        RoleAssignment assignment = scope == ScopeType.SITE
                ? RoleAssignment.onSite(person, role, organization, site, perimeter.actor(), now)
                : RoleAssignment.onOrganization(person, role, organization, perimeter.actor(), now);
        assignments.save(assignment);
        events.publishEvent(new RoleAssignedV1(assignment.id(), person, role.code(), scope, Optional.of(organization),
                Optional.ofNullable(site), perimeter.actor(), now));
        LOG.debug("Role {} given to person {} on {} {}", role.code(), person.value(), scope, scopeId);
        return assignment;
    }

    /**
     * Takes a role away (C10, M2); needs {@code role-assignment:revoke}, or {@code access:revoke} for a role of kind
     * {@code OCCUPANT} (RB-1); {@code LAST_ORGANIZATION_ADMIN} for the last {@code grantsAll} assignment (RB-2).
     */
    @Transactional
    public RoleAssignment end(Perimeter perimeter, UUID assignmentId, String reason) {
        RoleAssignment assignment = assignments.findById(assignmentId)
                .filter(a -> a.organizationId().isPresent())
                .orElseThrow(RoleAdministration::notFound);
        OrganizationId organization = assignment.organizationId().get();
        Role role = catalog.require(assignment.role());
        Optional<SiteId> site = assignment.siteId();
        if (site.isPresent()) {
            perimeter.requireVisible(organization, site.get());
        } else {
            perimeter.requireVisible(organization);
        }
        boolean allowed = can(perimeter, Permission.ROLE_ASSIGNMENT_REVOKE, organization, site)
                || role.kind() == RoleKind.OCCUPANT && can(perimeter, Permission.ACCESS_REVOKE, organization, site);
        if (!allowed) {
            throw new BusinessException("PERMISSION_DENIED", ErrorCategory.PERMISSION_DENIED, "Permission missing");
        }
        if (assignment.active() && role.grantsAll() && assignment.scopeType() == ScopeType.ORGANIZATION
                && assignments.countActive(catalog.grantsAllRoles(), organization) <= 1) {
            throw rule("LAST_ORGANIZATION_ADMIN");
        }
        return endAssignment(perimeter, assignment, reason, clock.instant());
    }

    /** Ends an assignment after the rights were checked and publishes {@code RoleAssignmentEndedV1}. */
    RoleAssignment endAssignment(Perimeter perimeter, RoleAssignment assignment, String reason, Instant now) {
        assignment.end(actor(perimeter), reason, now);
        assignments.save(assignment);
        events.publishEvent(new RoleAssignmentEndedV1(assignment.id(), assignment.personId(), assignment.role(),
                assignment.scopeType(), assignment.organizationId(), assignment.siteId(), perimeter.actor(), now));
        return assignment;
    }

    // ------------------------------------------------------------------------------- person adjustments (C9)

    /**
     * Adds or removes one permission for one person (C9); needs {@code permission-adjustment:manage}; {@code
     * PERMISSION_NOT_ADJUSTABLE}, {@code PERSON_HAS_NO_ROLE_ON_SCOPE}, {@code PERMISSION_ADJUSTMENT_USELESS}, {@code
     * PERMISSION_ADJUSTMENT_EXISTS}, {@code ROLE_NOT_EDITABLE} (a person holding a {@code grantsAll} role). Publishes
     * {@code PermissionAdjustedV1}.
     */
    @Transactional
    public PermissionAdjustment adjust(Perimeter perimeter, OrganizationId organization, PersonId person,
                                       ScopeType scope, UUID scopeId, Permission permission,
                                       AdjustmentEffect effect, String reason) {
        perimeter.require(Permission.PERMISSION_ADJUSTMENT_MANAGE, organization);
        if (!permission.adjustable()) {
            throw rule("PERMISSION_NOT_ADJUSTABLE");
        }
        Optional<SiteId> site = switch (scope) {
            case SITE -> {
                SiteId siteId = new SiteId(requireScopeId(scopeId));
                requireSiteOf(organization, siteId);
                yield Optional.of(siteId);
            }
            case ORGANIZATION -> {
                if (scopeId != null && !organization.value().equals(scopeId)) {
                    throw notFound();
                }
                yield Optional.empty();
            }
            case PLATFORM -> throw rule("PERMISSION_SCOPE_INVALID");
        };
        if (!permission.appliesTo(scope)) {
            throw rule("PERMISSION_SCOPE_INVALID");
        }
        List<RoleAssignment> personRoles = rolesCovering(person, organization, site);
        if (personRoles.isEmpty()) {
            throw rule("PERSON_HAS_NO_ROLE_ON_SCOPE");
        }
        if (personRoles.stream().anyMatch(a -> catalog.grantsAll(a.role()))) {
            throw rule("ROLE_NOT_EDITABLE");
        }
        Set<Permission> fromRoles = EnumSet.noneOf(Permission.class);
        personRoles.forEach(a -> fromRoles.addAll(rolePermissions.of(a.role(), organization)));
        boolean useless = effect == AdjustmentEffect.ADD ? fromRoles.contains(permission)
                : !fromRoles.contains(permission);
        if (useless) {
            throw rule("PERMISSION_ADJUSTMENT_USELESS");
        }
        if (adjustments.activeExists(person, organization, site, permission)) {
            throw new BusinessException("PERMISSION_ADJUSTMENT_EXISTS", ErrorCategory.CONFLICT, "Already adjusted");
        }
        Instant now = clock.instant();
        PermissionAdjustment adjustment = adjustments.save(PermissionAdjustment.of(person, organization, site,
                permission, effect, reason, actor(perimeter), now));
        events.publishEvent(new PermissionAdjustedV1(adjustment.id(), person, organization, adjustment.scopeType(),
                site, permission, effect.name(), perimeter.actor(), now));
        return adjustment;
    }

    /** Cancels an adjustment; needs {@code permission-adjustment:manage}. Publishes {@code PermissionAdjustmentCancelledV1}. */
    @Transactional
    public PermissionAdjustment cancel(Perimeter perimeter, UUID adjustmentId) {
        PermissionAdjustment adjustment = adjustments.findById(adjustmentId).orElseThrow(RoleAdministration::notFound);
        perimeter.require(Permission.PERMISSION_ADJUSTMENT_MANAGE, adjustment.organizationId());
        Instant now = clock.instant();
        adjustment.cancel(actor(perimeter), now);
        adjustments.save(adjustment);
        events.publishEvent(new PermissionAdjustmentCancelledV1(adjustment.id(), adjustment.personId(),
                adjustment.organizationId(), adjustment.permission(), adjustment.effect().name(), perimeter.actor(),
                now));
        return adjustment;
    }

    /**
     * What a person can do in the organization, scope by scope, and where each permission comes from (section 3.6);
     * needs {@code account:read}.
     */
    @Transactional(readOnly = true)
    public List<ScopePermissions> personPermissions(Perimeter perimeter, OrganizationId organization,
                                                    PersonId person) {
        perimeter.require(Permission.ACCOUNT_READ, organization);
        List<RoleAssignment> inOrganization = assignments.activeOfPerson(person).stream()
                .filter(a -> a.organizationId().equals(Optional.of(organization))).toList();
        List<PermissionAdjustment> personAdjustments = adjustments.activeOfPerson(person).stream()
                .filter(a -> a.organizationId().equals(organization) && a.permission() != null).toList();
        List<OrganizationRoleDelta> deltas = roles.activeDeltas(List.of(organization));
        Map<String, Optional<SiteId>> scopes = new LinkedHashMap<>();
        inOrganization.forEach(a -> scopes.put(String.valueOf(a.siteId().orElse(null)), a.siteId()));
        personAdjustments.forEach(a -> scopes.putIfAbsent(String.valueOf(a.siteId().orElse(null)), a.siteId()));

        List<ScopePermissions> result = new ArrayList<>();
        for (Optional<SiteId> site : scopes.values()) {
            List<RoleAssignment> covering = rolesCovering(person, organization, site);
            ScopeType scope = site.isPresent() ? ScopeType.SITE : ScopeType.ORGANIZATION;
            Map<Permission, Granted> granted = new LinkedHashMap<>();
            List<Granted> removed = new ArrayList<>();
            for (RoleAssignment assignment : covering) {
                RoleCode code = assignment.role();
                if (catalog.grantsAll(code)) {
                    Permission.organizationWide().stream().filter(p -> p.appliesTo(scope)).forEach(p ->
                            granted.put(p, new Granted(p, Origin.ALL, Optional.of(code), Optional.empty())));
                    continue;
                }
                catalog.defaults(code).stream().filter(p -> p.appliesTo(scope)).forEach(p -> granted.putIfAbsent(p,
                        new Granted(p, Origin.ROLE_DEFAULT, Optional.of(code), Optional.empty())));
                for (OrganizationRoleDelta delta : deltas) {
                    if (!delta.roleCode().equals(code) || delta.permission().isEmpty()
                            || !delta.permission().get().appliesTo(scope)) {
                        continue;
                    }
                    Permission p = delta.permission().get();
                    if (delta.effect() == AdjustmentEffect.ADD) {
                        granted.putIfAbsent(p, new Granted(p, Origin.ROLE_DELTA, Optional.of(code),
                                Optional.of(delta.id())));
                    } else if (granted.containsKey(p) && granted.get(p).origin() != Origin.ALL) {
                        granted.remove(p);
                        removed.add(new Granted(p, Origin.ROLE_DELTA, Optional.of(code), Optional.of(delta.id())));
                    }
                }
            }
            boolean everything = granted.values().stream().anyMatch(g -> g.origin() == Origin.ALL);
            if (!everything) {
                personAdjustments.stream().filter(a -> a.siteId().isEmpty() || a.siteId().equals(site))
                        .forEach(a -> {
                            Permission p = a.permission();
                            if (a.effect() == AdjustmentEffect.ADD && p.appliesTo(scope)) {
                                granted.putIfAbsent(p, new Granted(p, Origin.PERSON_ADDED, Optional.empty(),
                                        Optional.of(a.id())));
                            } else if (a.effect() == AdjustmentEffect.REMOVE && granted.containsKey(p)) {
                                granted.remove(p);
                                removed.add(new Granted(p, Origin.PERSON_REMOVED, Optional.empty(),
                                        Optional.of(a.id())));
                            }
                        });
            }
            Set<RoleCode> roleCodes = new java.util.LinkedHashSet<>();
            covering.forEach(a -> roleCodes.add(a.role()));
            result.add(new ScopePermissions(scope, site.map(SiteId::value).orElse(organization.value()), roleCodes,
                    List.copyOf(granted.values()), removed));
        }
        return result;
    }

    // ------------------------------------------------------------------------------------------- helpers

    /** Whether the caller holds the permission on the organization, or on the site when given. */
    private static boolean can(Perimeter perimeter, Permission permission, OrganizationId organization,
                               Optional<SiteId> site) {
        return site.map(s -> perimeter.can(permission, organization, s))
                .orElseGet(() -> perimeter.can(permission, organization));
    }

    /** The person's roles that apply to the scope: on the organization, or on this site. */
    private List<RoleAssignment> rolesCovering(PersonId person, OrganizationId organization, Optional<SiteId> site) {
        return assignments.activeOfPerson(person).stream()
                .filter(a -> a.organizationId().equals(Optional.of(organization)))
                .filter(a -> a.scopeType() == ScopeType.ORGANIZATION
                        || site.isPresent() && a.siteId().equals(site))
                .toList();
    }

    /** {@code NOT_FOUND} unless the site belongs to the organization. */
    void requireSiteOf(OrganizationId organization, SiteId site) {
        sites.findSite(site).filter(s -> s.organizationId().equals(organization))
                .orElseThrow(RoleAdministration::notFound);
    }

    /** A role is given only to someone the organization already knows: a role or an access there. */
    private void requireKnownToOrganization(PersonId person, OrganizationId organization) {
        boolean known = assignments.activeInOrganization(organization).stream()
                .anyMatch(a -> a.personId().equals(person))
                || memberships.ofPerson(person).stream().anyMatch(m -> m.organizationId().equals(organization));
        if (!known) {
            throw notFound();
        }
    }

    /** The scope identifier of a site scope, mandatory; {@code VALIDATION_FAILED} when missing. */
    private static UUID requireScopeId(UUID scopeId) {
        if (scopeId == null) {
            throw new BusinessException("VALIDATION_FAILED", ErrorCategory.VALIDATION, "scope.id is required");
        }
        return scopeId;
    }

    /** The person acting. */
    static PersonId actor(Perimeter perimeter) {
        return new PersonId(perimeter.actor().id());
    }

    /** A broken rule (422). */
    private static BusinessException rule(String code) {
        return new BusinessException(code, ErrorCategory.RULE_VIOLATION, code);
    }

    /** The single answer for something missing or outside the perimeter (M3). */
    static BusinessException notFound() {
        return new BusinessException("NOT_FOUND", ErrorCategory.NOT_FOUND, "Not found");
    }
}
