package com.almaraama.identity.api.events;

import com.almaraama.shared.id.PersonId;
import java.time.Instant;

/**
 * The account became active: the person chose their password with a "set my password" link (D4), or verified their
 * e-mail after self-registration (D1).
 */
public record PersonActivatedV1(PersonId personId, Instant occurredAt) {
}
