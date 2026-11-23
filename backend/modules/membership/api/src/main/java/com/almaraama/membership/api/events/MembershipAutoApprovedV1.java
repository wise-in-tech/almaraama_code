package com.almaraama.membership.api.events;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A registration was approved automatically by a pre-registration key (D3); {@code approverIds}: who may decide on the site, to be informed (K1). */
public record MembershipAutoApprovedV1(UUID membershipId, OrganizationId organizationId, SiteId siteId, PersonId personId,
                                       String personName, String siteName, List<PersonId> approverIds,
                                       Instant occurredAt) {
}
