package com.almaraama.sites.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;

/** A site was created (A1). */
public record SiteCreatedV1(SiteId siteId, OrganizationId organizationId, String name, Actor actor,
                            Instant occurredAt) {
}
