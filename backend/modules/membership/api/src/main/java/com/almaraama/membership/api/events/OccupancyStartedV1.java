package com.almaraama.membership.api.events;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import java.time.Instant;
import java.util.UUID;

/** A person became owner or tenant of a unit (E1, E2). */
public record OccupancyStartedV1(UUID occupancyId, OrganizationId organizationId, SiteId siteId, UnitId unitId,
                                 PersonId personId, String role, Instant occurredAt) {
}
