package com.almaraama.membership.adapters.out.persistence;

import com.almaraama.membership.domain.OrganizationRoleDelta;
import java.util.Collection;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Differences of the organizations from the role defaults (C11). No delete method exists (M2). */
interface SpringDataRoleDeltas extends Repository<OrganizationRoleDelta, UUID> {

    /** The active differences of these organizations. */
    List<OrganizationRoleDelta> findByOrganizationIdInAndCancelledAtIsNull(Collection<UUID> organizationIds);

    /** Inserts or updates a difference. */
    OrganizationRoleDelta save(OrganizationRoleDelta delta);
}
