package com.almaraama.membership.domain;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Out port: person-level permission adjustments. No delete (M2). */
public interface PermissionAdjustmentRepository {

    /** Loads an adjustment. */
    Optional<PermissionAdjustment> findById(UUID id);

    /** The person's adjustments in force. */
    List<PermissionAdjustment> activeOfPerson(PersonId personId);

    /** Whether the same permission is already adjusted for the person on the same scope. */
    boolean activeExists(PersonId personId, OrganizationId organizationId, Optional<SiteId> siteId,
                         Permission permission);

    /** Persons of the organization who have at least one active adjustment. */
    List<PersonId> personsWithActiveAdjustments(OrganizationId organizationId);

    /** Stores an adjustment. */
    PermissionAdjustment save(PermissionAdjustment adjustment);
}
