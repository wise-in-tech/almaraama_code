package com.almaraama.membership.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;
import java.util.UUID;

/** A self-registration was rejected (D3). Kept as REJECTED (M2). */
public record MembershipRejectedV1(UUID membershipId, OrganizationId organizationId, SiteId siteId, PersonId personId,
                                   String siteName, Actor actor, Instant occurredAt) {
}
