package com.almaraama.membership.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;
import java.util.UUID;

/** A pre-registration received a new key; the old one stopped working (D3). Never the key. */
public record PreRegistrationKeyRenewedV1(UUID preRegistrationId, OrganizationId organizationId, SiteId siteId, Actor actor,
                                          Instant occurredAt) {
}
