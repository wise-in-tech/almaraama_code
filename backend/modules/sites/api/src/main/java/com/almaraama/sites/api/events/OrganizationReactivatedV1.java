package com.almaraama.sites.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import java.time.Instant;

/** A deactivated organization was given back its access (C12, ADR-035). */
public record OrganizationReactivatedV1(OrganizationId organizationId, String reason, Actor actor, Instant occurredAt) {
}
