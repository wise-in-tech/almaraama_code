package com.almaraama.identity.api.events;

import com.almaraama.shared.id.PersonId;
import java.time.Instant;
import java.util.UUID;

/** A person signed in with phone and PIN on a device (D4). Audited (security baseline). */
public record LoginSucceededV1(PersonId personId, UUID deviceId, Instant occurredAt) {
}
