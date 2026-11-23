package com.almaraama.membership.adapters.out.persistence;

import com.almaraama.membership.domain.RoleAssignment;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Role assignments, schema membership. No delete (M2). */
interface SpringDataRoleAssignments extends Repository<RoleAssignment, UUID> {

    /** Loads a role assignment. */
    Optional<RoleAssignment> findById(UUID id);

    /** A person's roles in force. */
    List<RoleAssignment> findByPersonIdAndEndedAtIsNull(UUID personId);

    /** Roles in force on an organization or its sites. */
    List<RoleAssignment> findByOrganizationIdAndEndedAtIsNull(UUID organizationId);

    @Query("select count(a) from RoleAssignment a where a.role in :roles and a.organizationId = :organizationId"
            + " and a.scopeType = com.almaraama.shared.access.ScopeType.ORGANIZATION and a.endedAt is null")
    /** Assignments in force of these roles on the organization itself. */
    long countActiveOnOrganization(Collection<String> roles, UUID organizationId);

    /** Inserts or updates a role assignment. */
    RoleAssignment save(RoleAssignment assignment);
}
