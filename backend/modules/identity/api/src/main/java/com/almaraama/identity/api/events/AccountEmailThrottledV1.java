package com.almaraama.identity.api.events;

import com.almaraama.shared.id.PersonId;
import java.time.Instant;

/** An account e-mail was not sent because the hourly limit for this person and kind was reached (ADR-031). */
public record AccountEmailThrottledV1(PersonId personId, AccountEmailKind kind, Instant occurredAt) {
}
