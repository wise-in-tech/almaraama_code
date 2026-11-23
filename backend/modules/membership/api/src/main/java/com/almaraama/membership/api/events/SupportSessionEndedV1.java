package com.almaraama.membership.api.events;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import java.time.Instant;
import java.util.UUID;

/** A platform administrator ended a support session before it expired (C12, ADR-035). */
public record SupportSessionEndedV1(UUID sessionId, PersonId administratorId, OrganizationId organizationId, Instant occurredAt) {
}
