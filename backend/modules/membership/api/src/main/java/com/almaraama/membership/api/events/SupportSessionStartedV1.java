package com.almaraama.membership.api.events;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import java.time.Instant;
import java.util.UUID;

/** A platform administrator opened a support session on an organization, with a reason, for one hour (C12, ADR-035). */
public record SupportSessionStartedV1(UUID sessionId, PersonId administratorId, OrganizationId organizationId, String reason,
                                      Instant expiresAt, Instant occurredAt) {
}
