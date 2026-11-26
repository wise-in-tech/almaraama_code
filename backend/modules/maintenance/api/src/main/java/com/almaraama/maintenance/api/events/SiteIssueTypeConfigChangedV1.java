package com.almaraama.maintenance.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;
import java.util.UUID;

/**
 * Where tickets of an issue type go on a site, or who sees them, changed (H4, H8). Audited with the values before and
 * after (routing, designated people, visibility, state). Versioned (CF-5).
 */
public record SiteIssueTypeConfigChangedV1(OrganizationId organizationId, SiteId siteId, UUID issueTypeId, String before,
                                       String after, Actor actor, Instant occurredAt) {
}
