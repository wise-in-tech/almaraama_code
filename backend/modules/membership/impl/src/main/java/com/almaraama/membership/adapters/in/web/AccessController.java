package com.almaraama.membership.adapters.in.web;

import com.almaraama.membership.adapters.in.web.MembershipWebDtos.EffectivePermission;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.OrganizationRoleResponse;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.Page;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.PermissionAdjustmentRequest;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.PermissionAdjustmentResponse;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.PermissionResponse;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.PersonPermissionsResponse;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.PersonRef;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.RoleDeltaResponse;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.ReasonRequest;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.RemovedPermission;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.RoleAssignmentRequest;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.RoleAssignmentResponse;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.RoleDefinitionResponse;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.Scope;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.ScopePermissionsResponse;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.SetRolePermissionsRequest;
import com.almaraama.membership.application.RoleAdministration;
import com.almaraama.membership.application.RoleAdministration.OrganizationRoleView;
import com.almaraama.membership.domain.PermissionAdjustment;
import com.almaraama.membership.domain.RoleAssignment;
import com.almaraama.membership.domain.RoleAssignmentRepository;
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
import jakarta.validation.Valid;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Map;
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

/** Roles, their permissions per organization and person-level adjustments (C1, C8 to C10, ADR-026). */
@RestController
class AccessController {

    private final RoleAdministration roles;
    private final Names names;

    /**
     * @param roles role and permission use cases
     * @param names resolves people's and scopes' names for the responses
     */
    AccessController(RoleAdministration roles, Names names) {
        this.roles = roles;
        this.names = names;
    }

    /** {@code GET /v1/permissions}: the catalogue of permissions with their scopes and labels. */
    @GetMapping("/v1/permissions")
    List<PermissionResponse> permissions(Perimeter perimeter) {
        return Arrays.stream(Permission.values()).map(p -> new PermissionResponse(p.code(),
                p.scopes().stream().sorted().toList(), p.adjustable(), p.sensitive(), AccessLabels.label(p),
                AccessLabels.description(p))).toList();
    }

    /** {@code GET /v1/roles}: the roles with their platform default permissions (spec section 3). */
    @GetMapping("/v1/roles")
    List<RoleDefinitionResponse> roleCatalogue(Perimeter perimeter) {
        return roles.catalogue().stream().map(role -> new RoleDefinitionResponse(role.code().value(), role.kind(),
                role.scopes().stream().sorted().toList(), role.organizationId().map(OrganizationId::value)
                .orElse(null), AccessLabels.role(role), role.grantsAll(), role.editable(),
                MembershipWebDtos.codes(roles.defaults(role.code())))).toList();
    }

    /**
     * {@code GET /v1/organizations/{id}/roles}: each role's defaults, differences and effective permissions in the
     * organization (C9, C11); needs {@code permission-adjustment:manage}.
     */
    @GetMapping("/v1/organizations/{organizationId}/roles")
    List<OrganizationRoleResponse> organizationRoles(Perimeter perimeter, @PathVariable UUID organizationId) {
        return roles.organizationRoles(perimeter, new OrganizationId(organizationId)).stream()
                .map(this::response).toList();
    }

    /**
     * {@code PUT /v1/organizations/{id}/roles/{roleCode}}: the permissions wanted for a role in the organization; the
     * differences from the defaults are stored (C9, C11); needs {@code permission-adjustment:manage}.
     */
    @PutMapping("/v1/organizations/{organizationId}/roles/{roleCode}")
    OrganizationRoleResponse setRole(Perimeter perimeter, @PathVariable UUID organizationId,
                                     @PathVariable String roleCode,
                                     @Valid @RequestBody SetRolePermissionsRequest request) {
        return response(roles.setRolePermissions(perimeter, new OrganizationId(organizationId), code(roleCode),
                MembershipWebDtos.permissions(request.permissions()), request.reason()));
    }

    /**
     * {@code POST /v1/organizations/{id}/roles/{roleCode}/reset}: back to the defaults (section 3.6); needs {@code
     * permission-adjustment:manage}.
     */
    @PostMapping("/v1/organizations/{organizationId}/roles/{roleCode}/reset")
    OrganizationRoleResponse resetRole(Perimeter perimeter, @PathVariable UUID organizationId,
                                       @PathVariable String roleCode) {
        return response(roles.resetRole(perimeter, new OrganizationId(organizationId), code(roleCode)));
    }

    /** {@code GET /v1/organizations/{id}/role-assignments}: who holds which role, filtered by person, site and role. */
    @GetMapping("/v1/organizations/{organizationId}/role-assignments")
    Page<RoleAssignmentResponse> assignments(Perimeter perimeter, @PathVariable UUID organizationId,
                                             @RequestParam(required = false) UUID personId,
                                             @RequestParam(required = false) UUID siteId,
                                             @RequestParam(required = false) String roleCode,
                                             @RequestParam(defaultValue = "false") boolean includeEnded,
                                             @RequestParam(required = false) Integer page,
                                             @RequestParam(required = false) Integer size) {
        PageResult<RoleAssignment> found = roles.assignments(perimeter, new OrganizationId(organizationId),
                new RoleAssignmentRepository.Filter(personId == null ? null : new PersonId(personId),
                        siteId == null ? null : new SiteId(siteId), roleCode == null ? null : code(roleCode),
                        includeEnded), PageQuery.of(page, size));
        return Page.of(found, assignmentMapper(found.items()));
    }

    /** {@code POST /v1/organizations/{id}/role-assignments}: gives a role; needs {@code role-assignment:grant} (RB-1). */
    @PostMapping("/v1/organizations/{organizationId}/role-assignments")
    @ResponseStatus(HttpStatus.CREATED)
    RoleAssignmentResponse assign(Perimeter perimeter, @PathVariable UUID organizationId,
                                  @Valid @RequestBody RoleAssignmentRequest request) {
        RoleAssignment assignment = roles.assign(perimeter, new OrganizationId(organizationId),
                new PersonId(request.personId()), code(request.roleCode()), request.scope().type(),
                request.scope().id());
        return assignmentMapper(List.of(assignment)).apply(assignment);
    }

    /**
     * {@code POST /v1/role-assignments/{id}/end}: takes a role away; {@code LAST_ORGANIZATION_ADMIN} for the last owner
     * (RB-2).
     */
    @PostMapping("/v1/role-assignments/{assignmentId}/end")
    RoleAssignmentResponse end(Perimeter perimeter, @PathVariable UUID assignmentId,
                               @Valid @RequestBody(required = false) ReasonRequest request) {
        RoleAssignment assignment = roles.end(perimeter, assignmentId, request == null ? null : request.reason());
        return assignmentMapper(List.of(assignment)).apply(assignment);
    }

    /**
     * {@code GET /v1/organizations/{id}/people/{personId}/permissions}: effective permissions per scope with their
     * origin.
     */
    @GetMapping("/v1/organizations/{organizationId}/people/{personId}/permissions")
    PersonPermissionsResponse personPermissions(Perimeter perimeter, @PathVariable UUID organizationId,
                                                @PathVariable UUID personId) {
        List<RoleAdministration.ScopePermissions> scopes = roles.personPermissions(perimeter,
                new OrganizationId(organizationId), new PersonId(personId));
        Map<UUID, String> scopeNames = names.scopes(List.of(organizationId),
                scopes.stream().filter(s -> s.scopeType() == ScopeType.SITE).map(s -> s.scopeId()).toList());
        return new PersonPermissionsResponse(personId, scopes.stream().map(s -> new ScopePermissionsResponse(
                new Scope(s.scopeType(), s.scopeId()), scopeNames.get(s.scopeId()),
                s.roles().stream().map(RoleCode::value).sorted().toList(),
                s.granted().stream().sorted(Comparator.comparing(g -> g.permission().code()))
                        .map(g -> new EffectivePermission(g.permission().code(), g.origin().name(),
                                g.role().map(RoleCode::value).orElse(null), g.adjustmentId().orElse(null)))
                        .toList(),
                s.removed().stream()
                        .map(g -> new RemovedPermission(g.permission().code(), g.origin().name(),
                                g.adjustmentId().orElse(null))).toList()))
                .toList());
    }

    /**
     * {@code POST /v1/organizations/{id}/permission-adjustments}: adds or removes one permission for one person (C9).
     */
    @PostMapping("/v1/organizations/{organizationId}/permission-adjustments")
    @ResponseStatus(HttpStatus.CREATED)
    PermissionAdjustmentResponse adjust(Perimeter perimeter, @PathVariable UUID organizationId,
                                        @Valid @RequestBody PermissionAdjustmentRequest request) {
        PermissionAdjustment adjustment = roles.adjust(perimeter, new OrganizationId(organizationId),
                new PersonId(request.personId()), request.scope().type(), request.scope().id(),
                MembershipWebDtos.permission(request.permission()), request.effect(), request.reason());
        return response(adjustment);
    }

    /** {@code POST /v1/permission-adjustments/{id}/cancel}: the role's permissions apply again. */
    @PostMapping("/v1/permission-adjustments/{adjustmentId}/cancel")
    PermissionAdjustmentResponse cancel(Perimeter perimeter, @PathVariable UUID adjustmentId) {
        return response(roles.cancel(perimeter, adjustmentId));
    }

    // ------------------------------------------------------------------------------------------- mapping

    /** Response body of a role's permissions in an organization. */
    /** A role's defaults, differences (with their authors) and effective permissions. */
    private OrganizationRoleResponse response(OrganizationRoleView view) {
        Map<UUID, PersonRef> refs = names.people(view.deltas().stream().map(d -> d.createdBy().value()).toList());
        return new OrganizationRoleResponse(view.role().code().value(), MembershipWebDtos.codes(view.defaults()),
                view.deltas().stream().filter(d -> d.permission().isPresent())
                        .map(d -> new RoleDeltaResponse(d.id(), d.permission().get().code(), d.effect(),
                                refs.get(d.createdBy().value()), d.createdAt())).toList(),
                MembershipWebDtos.codes(view.effective()), view.customized(), view.editable());
    }

    /** A role code from the path or the body; {@code ROLE_UNKNOWN} when malformed. */
    private static RoleCode code(String value) {
        try {
            return RoleCode.of(value);
        } catch (IllegalArgumentException e) {
            throw new BusinessException("ROLE_UNKNOWN", ErrorCategory.RULE_VIOLATION, "Unknown role");
        }
    }

    /** Maps assignments to responses, resolving all people and scope names of the batch at once. */
    private java.util.function.Function<RoleAssignment, RoleAssignmentResponse> assignmentMapper(
            List<RoleAssignment> items) {
        List<UUID> people = new ArrayList<>();
        items.forEach(a -> {
            people.add(a.personId().value());
            people.add(Names.id(a.assignedBy()));
            a.endedBy().ifPresent(p -> people.add(p.value()));
        });
        Map<UUID, PersonRef> refs = names.people(people);
        Map<UUID, String> scopeNames = names.scopes(
                items.stream().map(a -> a.organizationId().map(OrganizationId::value).orElse(null)).toList(),
                items.stream().map(a -> a.siteId().map(SiteId::value).orElse(null)).toList());
        return a -> {
            UUID scopeId = a.siteId().map(SiteId::value)
                    .orElse(a.organizationId().map(OrganizationId::value).orElse(null));
            return new RoleAssignmentResponse(a.id(), refs.get(a.personId().value()), a.role().value(),
                    new Scope(a.scopeType(), scopeId), scopeId == null ? null : scopeNames.get(scopeId),
                    refs.get(Names.id(a.assignedBy())), a.assignedAt(),
                    a.endedBy().map(p -> refs.get(p.value())).orElse(null), a.endedAt().orElse(null),
                    a.endReason().orElse(null));
        };
    }

    /** Response body of an adjustment with the names of the people involved. */
    private PermissionAdjustmentResponse response(PermissionAdjustment a) {
        Map<UUID, PersonRef> refs = names.people(List.of(a.personId().value(), a.createdBy().value(),
                a.cancelledBy().map(PersonId::value).orElse(a.createdBy().value())));
        UUID scopeId = a.siteId().map(SiteId::value).orElse(a.organizationId().value());
        return new PermissionAdjustmentResponse(a.id(), refs.get(a.personId().value()),
                new Scope(a.scopeType(), scopeId), a.permission().code(), a.effect(), a.reason().orElse(null),
                refs.get(a.createdBy().value()), a.createdAt(),
                a.cancelledBy().map(p -> refs.get(p.value())).orElse(null), a.cancelledAt().orElse(null));
    }
}
