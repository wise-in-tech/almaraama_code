package com.almaraama.membership.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;

/** Registration on a site was activated or deactivated by its QR code settings (D2, criterion 4). */
public record RegistrationQrEnabledChangedV1(SiteId siteId, OrganizationId organizationId, boolean enabled, Actor actor, Instant occurredAt) {
}
