package com.almaraama.membership.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;
import java.util.UUID;

/** A person's access to a site was ended (D6, M2). */
public record MembershipDeactivatedV1(UUID membershipId, OrganizationId organizationId, SiteId siteId,
                                      PersonId personId, Actor actor, Instant occurredAt) {
}
