package com.almaraama.assets.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;
import java.util.UUID;

/**
 * The QR code label of an equipment was replaced (G4): the old code stops answering. Versioned immutable event (CF-5).
 *
 * @param assetId the equipment
 * @param actor   who did it
 */
public record AssetQrRotatedV1(UUID assetId, OrganizationId organizationId, SiteId siteId, String name, Actor actor,
                 Instant occurredAt) {
}
