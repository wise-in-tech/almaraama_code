package com.almaraama.sites.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import java.time.Instant;

/** An organization was deactivated by our team; its people lose access, nothing is deleted (C12, ADR-035). */
public record OrganizationDeactivatedV1(OrganizationId organizationId, String reason, Actor actor, Instant occurredAt) {
}
