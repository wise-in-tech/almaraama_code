package com.almaraama.sites.api.events;

import com.almaraama.shared.actor.Actor;
import java.time.Instant;
import java.util.UUID;

/** A provisional zone was validated by the platform administrators, keeping its addresses (A8). */
public record ZoneValidatedV1(UUID zoneId, String countryCode, String code, Actor actor, Instant occurredAt) {
}
