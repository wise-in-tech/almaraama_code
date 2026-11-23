package com.almaraama.identity.api.events;

import com.almaraama.shared.id.PersonId;
import java.time.Instant;

/**
 * The person set or reset their password (D4, D5). {@code reset} is true for a forgotten password, after which every
 * session of the person was ended (ASVS V3.3.3).
 */
public record PasswordChangedV1(PersonId personId, boolean reset, Instant occurredAt) {
}
