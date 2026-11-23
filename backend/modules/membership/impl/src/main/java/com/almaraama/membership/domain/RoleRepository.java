package com.almaraama.membership.domain;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.access.RoleCode;
import com.almaraama.shared.id.OrganizationId;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Out port: the roles and their default permissions (data loaded by migration, read only for the application), and
 * the organizations' differences from those defaults (C11).
 */
public interface RoleRepository {

    /** Every active role, by sort order. */
    List<Role> roles();

    /** Every role's default permissions (specification section 3.3); codes unknown to the catalogue are ignored. */
    Map<RoleCode, Set<Permission>> defaults();

    /** The active differences of these organizations, all roles. */
    List<OrganizationRoleDelta> activeDeltas(Collection<OrganizationId> organizations);

    /** Stores a difference. */
    OrganizationRoleDelta save(OrganizationRoleDelta delta);
}
