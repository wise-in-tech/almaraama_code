package com.almaraama.assets.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;
import java.util.UUID;

/**
 * An equipment was archived (M2): its QR code stops answering and no new ticket may name it. Versioned immutable event (CF-5).
 *
 * @param assetId the equipment
 * @param actor   who did it
 */
public record AssetArchivedV1(UUID assetId, OrganizationId organizationId, SiteId siteId, String name, Actor actor,
                 Instant occurredAt) {
}
