package com.almaraama.membership.domain;

import com.almaraama.shared.access.RoleCode;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Out port: role assignments. No delete (M2). */
public interface RoleAssignmentRepository {

    /** Search criteria inside one organization; {@code null} means any. */
    record Filter(PersonId personId, SiteId siteId, RoleCode role, boolean includeEnded) {
    }

    /** Loads a role assignment. */
    Optional<RoleAssignment> findById(UUID id);

    /** The person's roles in force, on every scope. */
    List<RoleAssignment> activeOfPerson(PersonId personId);

    /** Active assignments on the organization or one of its sites. */
    List<RoleAssignment> activeInOrganization(OrganizationId organizationId);

    /** Assignments of an organization and its sites matching the filter, newest first. */
    PageResult<RoleAssignment> search(OrganizationId organizationId, Filter filter, PageQuery page);

    /** The person's assignment in force of this role on this exact scope. */
    Optional<RoleAssignment> active(PersonId personId, RoleCode role, ScopeType scope, OrganizationId organizationId,
                                    SiteId siteId);

    /** How many assignments of these roles are in force on the organization itself (RB-2, the grants-all roles). */
    long countActive(Set<RoleCode> roles, OrganizationId organizationId);

    /** Stores a role assignment. */
    RoleAssignment save(RoleAssignment assignment);
}
