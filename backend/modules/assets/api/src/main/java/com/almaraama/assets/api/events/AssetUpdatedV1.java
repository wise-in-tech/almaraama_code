package com.almaraama.assets.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;
import java.util.UUID;

/**
 * An equipment was changed (location, state, characteristics, documents or warranties). Versioned immutable event (CF-5).
 *
 * @param assetId the equipment
 * @param actor   who did it
 */
public record AssetUpdatedV1(UUID assetId, OrganizationId organizationId, SiteId siteId, String name, Actor actor,
                 Instant occurredAt) {
}
