package com.almaraama.membership.api.events;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import java.time.Instant;
import java.util.UUID;

/** An occupancy ended; it is kept with its end date and reason (E2, M2). */
public record OccupancyEndedV1(UUID occupancyId, OrganizationId organizationId, SiteId siteId, UnitId unitId,
                               PersonId personId, String role, Instant occurredAt) {
}
