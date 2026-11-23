package com.almaraama.identity.api.events;

import com.almaraama.shared.id.PersonId;
import java.time.Instant;
import java.util.Optional;

/**
 * A sign-in failed. {@code personId} is empty when the phone number matches no active account; the phone number
 * itself is never put in an event (personal data).
 */
public record LoginFailedV1(Optional<PersonId> personId, Instant occurredAt) {
}
