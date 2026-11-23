package com.almaraama.membership.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;
import java.util.UUID;

/** A pending pre-registration was cancelled; its key stopped working (D3, M2). */
public record PreRegistrationCancelledV1(UUID preRegistrationId, OrganizationId organizationId, SiteId siteId, Actor actor,
                                         Instant occurredAt) {
}
