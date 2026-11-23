package com.almaraama.membership.api.events;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;
import java.util.UUID;

/** A pre-registration key was used by the person it was meant for (D3); {@code autoApproved} is false when a planned unit was no longer free. */
public record PreRegistrationUsedV1(UUID preRegistrationId, OrganizationId organizationId, SiteId siteId, PersonId personId,
                                    UUID membershipId, boolean autoApproved, Instant occurredAt) {
}
