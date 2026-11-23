package com.almaraama.identity.api.events;

import com.almaraama.shared.id.PersonId;
import java.time.Instant;
import java.util.UUID;

/**
 * A refresh token was presented a second time: it was probably stolen. The session on that device is ended.
 */
public record RefreshTokenReuseDetectedV1(PersonId personId, UUID deviceId, Instant occurredAt) {
}
