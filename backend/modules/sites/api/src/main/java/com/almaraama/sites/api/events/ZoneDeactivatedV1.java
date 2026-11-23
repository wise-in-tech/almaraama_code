package com.almaraama.sites.api.events;

import com.almaraama.shared.actor.Actor;
import java.time.Instant;
import java.util.UUID;

/** A zone was deactivated: kept, no longer offered (A8, M2). */
public record ZoneDeactivatedV1(UUID zoneId, String countryCode, String code, Actor actor, Instant occurredAt) {
}
