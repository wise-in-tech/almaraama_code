package com.almaraama.membership.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;
import java.util.UUID;

/** A person was pre-registered with planned units and a key (D3); {@code origin}: MANUAL or IMPORT. Never the key. */
public record PreRegistrationCreatedV1(UUID preRegistrationId, OrganizationId organizationId, SiteId siteId, String origin, int plannedUnits, Actor actor,
                                       Instant occurredAt) {
}
