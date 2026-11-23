package com.almaraama.sites.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.sites.api.UnitType;
import java.time.Instant;
import java.util.Optional;

/** A unit was changed, deactivated or reactivated. */
public record UnitUpdatedV1(UnitId unitId, SiteId siteId, OrganizationId organizationId,
                            Optional<BuildingId> buildingId, UnitType type, String reference, boolean active,
                            Actor actor, Instant occurredAt) {
}
