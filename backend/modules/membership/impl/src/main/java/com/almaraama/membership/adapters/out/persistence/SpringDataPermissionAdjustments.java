package com.almaraama.membership.adapters.out.persistence;

import com.almaraama.membership.domain.PermissionAdjustment;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Person-level permission adjustments, schema membership. No delete (M2). */
interface SpringDataPermissionAdjustments extends Repository<PermissionAdjustment, UUID> {

    /** Loads an adjustment. */
    Optional<PermissionAdjustment> findById(UUID id);

    /** A person's adjustments in force. */
    List<PermissionAdjustment> findByPersonIdAndCancelledAtIsNull(UUID personId);

    @Query("select distinct a.personId from PermissionAdjustment a where a.organizationId = :organizationId"
            + " and a.cancelledAt is null")
    /** People of the organization with at least one adjustment in force. */
    List<UUID> personsWithActiveAdjustments(UUID organizationId);

    /** Inserts or updates an adjustment. */
    PermissionAdjustment save(PermissionAdjustment adjustment);
}
