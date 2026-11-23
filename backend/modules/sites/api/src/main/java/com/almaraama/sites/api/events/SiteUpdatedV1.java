package com.almaraama.sites.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;

/** A site was renamed, moved, archived or reactivated. */
public record SiteUpdatedV1(SiteId siteId, OrganizationId organizationId, String name, boolean active, Actor actor,
                            Instant occurredAt) {
}
