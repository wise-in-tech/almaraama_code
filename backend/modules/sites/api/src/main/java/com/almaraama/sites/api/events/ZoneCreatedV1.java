package com.almaraama.sites.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import java.time.Instant;
import java.util.UUID;

/**
 * A provisional zone was created by an organization whose country is not loaded (A8); the platform administrators
 * validate or merge it later (sprint 2).
 */
public record ZoneCreatedV1(UUID zoneId, String countryCode, String code, int level, OrganizationId organizationId,
                            Actor actor, Instant occurredAt) {
}
