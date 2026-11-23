package com.almaraama.membership.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;

/** The registration QR code of a site was replaced; the old one stopped working (D2, criterion 4). */
public record RegistrationQrRotatedV1(SiteId siteId, OrganizationId organizationId, Actor actor, Instant occurredAt) {
}
