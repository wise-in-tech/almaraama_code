package com.almaraama.identity.api.events;

import com.almaraama.shared.id.PersonId;
import java.time.Instant;

/** Too many failed attempts: sign-in is refused until {@code lockedUntil} (progressive lockout). */
public record CredentialLockedV1(PersonId personId, int failedAttempts, Instant lockedUntil, Instant occurredAt) {
}
