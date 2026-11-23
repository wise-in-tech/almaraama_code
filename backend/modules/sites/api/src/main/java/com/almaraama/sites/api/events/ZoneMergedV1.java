package com.almaraama.sites.api.events;

import com.almaraama.shared.actor.Actor;
import java.time.Instant;
import java.util.UUID;

/** A provisional zone was merged into a validated zone; its current addresses moved there (A8, M2). */
public record ZoneMergedV1(UUID zoneId, UUID targetZoneId, int movedAddresses, Actor actor, Instant occurredAt) {
}
