package com.almaraama.assets.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;
import java.util.UUID;

/**
 * An equipment was added to a site (G1, G2), by hand or by an Excel import. Versioned immutable event (CF-5).
 *
 * @param assetId the equipment
 * @param actor   who did it
 */
public record AssetCreatedV1(UUID assetId, OrganizationId organizationId, SiteId siteId, String name, Actor actor,
                 Instant occurredAt) {
}
