package com.almaraama.identity.api.events;

import com.almaraama.shared.id.PersonId;
import java.time.Instant;

/** The person proved they read their e-mail, through a verification, "set my password" or reset link (D1). */
public record EmailVerifiedV1(PersonId personId, Instant occurredAt) {
}
