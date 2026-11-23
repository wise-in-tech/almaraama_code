package com.almaraama.sites.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.sites.api.OrganizationType;
import java.time.Instant;

/** A client organization was created (A1). */
public record OrganizationCreatedV1(OrganizationId organizationId, OrganizationType type, String name, Actor actor,
                                    Instant occurredAt) {
}
