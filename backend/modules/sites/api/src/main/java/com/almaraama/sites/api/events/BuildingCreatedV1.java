package com.almaraama.sites.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;

/** A building was created (A2). */
public record BuildingCreatedV1(BuildingId buildingId, SiteId siteId, OrganizationId organizationId, String name,
                                Actor actor, Instant occurredAt) {
}
