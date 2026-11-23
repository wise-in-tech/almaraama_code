package com.almaraama.membership.api.events;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/** A person asked for access to a site (D3). {@code approverIds}: who may decide, to be notified (K1). */
public record MembershipRequestedV1(UUID membershipId, OrganizationId organizationId, SiteId siteId, PersonId personId,
                                    String personName, String siteName, List<PersonId> approverIds,
                                    Instant occurredAt) {
}
