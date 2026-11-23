package com.almaraama.customization.api.events;

import com.almaraama.shared.actor.Actor;
import java.time.Instant;

/**
 * A text of the dictionary was changed or rolled back by a platform administrator (M6, ADR-029). Carries the key and
 * language, never the text itself.
 */
public record TextUpdatedV1(String key, String language, String origin, Actor actor, Instant occurredAt) {
}
