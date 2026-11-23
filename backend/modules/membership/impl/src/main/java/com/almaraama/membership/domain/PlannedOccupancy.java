package com.almaraama.membership.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.UnitId;
import java.util.Objects;

/**
 * A unit planned for a pre-registered person (D3, E1 to E4): given as an occupancy when the key is used.
 *
 * @param primaryTenant only for a {@code TENANT} (E3); {@code OCCUPANCY_PRIMARY_TENANT_NOT_TENANT} otherwise
 */
public record PlannedOccupancy(UnitId unitId, OccupancyRole role, boolean primaryTenant) {

    public PlannedOccupancy {
        Objects.requireNonNull(unitId, "unitId");
        Objects.requireNonNull(role, "role");
        if (primaryTenant && role != OccupancyRole.TENANT) {
            throw new BusinessException("OCCUPANCY_PRIMARY_TENANT_NOT_TENANT", ErrorCategory.RULE_VIOLATION,
                    "Only a tenant can be the primary tenant (E3)");
        }
    }
}
