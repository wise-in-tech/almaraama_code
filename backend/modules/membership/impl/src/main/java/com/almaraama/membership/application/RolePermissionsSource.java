package com.almaraama.membership.application;

import com.almaraama.membership.domain.AdjustmentEffect;
import com.almaraama.membership.domain.OrganizationRoleDelta;
import com.almaraama.membership.domain.PermissionCalculator;
import com.almaraama.membership.domain.RoleRepository;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.access.RoleCode;
import com.almaraama.shared.id.OrganizationId;
import java.util.ArrayList;
import java.util.Collection;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * The permissions of each role in each organization (C11): its defaults, plus the organization's {@code ADD}
 * differences, minus its {@code REMOVE} differences. Only adjustable permissions are ever added or removed (C9).
 */
@Component
class RolePermissionsSource {

    private final RoleCatalog catalog;
    private final RoleRepository roles;

    /**
     * @param catalog roles and defaults
     * @param roles   differences of the organizations
     */
    RolePermissionsSource(RoleCatalog catalog, RoleRepository roles) {
        this.catalog = catalog;
        this.roles = roles;
    }

    /** Loads, in one query, the differences of the given organizations and returns the role permissions there. */
    PermissionCalculator.RolePermissions forOrganizations(Collection<OrganizationId> organizations) {
        Map<OrganizationId, List<OrganizationRoleDelta>> deltas = new HashMap<>();
        if (!organizations.isEmpty()) {
            roles.activeDeltas(organizations).forEach(d ->
                    deltas.computeIfAbsent(d.organizationId(), k -> new ArrayList<>()).add(d));
        }
        return new PermissionCalculator.RolePermissions() {
            @Override
            public boolean grantsAll(RoleCode role) {
                return catalog.grantsAll(role);
            }

            @Override
            public Set<Permission> of(RoleCode role, Optional<OrganizationId> organization) {
                return apply(role, organization.map(id -> deltas.getOrDefault(id, List.of())).orElse(List.of()));
            }
        };
    }

    /** The permissions of one role in one organization. */
    Set<Permission> of(RoleCode role, OrganizationId organization) {
        return forOrganizations(Set.of(organization)).of(role, Optional.of(organization));
    }

    /** Defaults of the role, then its active differences in the organization. */
    private Set<Permission> apply(RoleCode role, List<OrganizationRoleDelta> deltas) {
        Set<Permission> permissions = EnumSet.noneOf(Permission.class);
        permissions.addAll(catalog.defaults(role));
        for (OrganizationRoleDelta delta : deltas) {
            if (!delta.roleCode().equals(role) || delta.permission().isEmpty()
                    || !delta.permission().get().adjustable()) {
                continue;
            }
            if (delta.effect() == AdjustmentEffect.ADD) {
                permissions.add(delta.permission().get());
            } else {
                permissions.remove(delta.permission().get());
            }
        }
        return permissions;
    }
}
