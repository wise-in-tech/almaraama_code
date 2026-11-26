package com.almaraama.maintenance.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

/**
 * A ticket was created (H1, H4, K1). Carries the recipients computed with their real permissions: the managers of the
 * site and the technicians designated by the routing. Versioned immutable event (CF-5).
 *
 * @param reference   readable reference ({@code T-000123})
 * @param common      true for a common ticket (H8); notifications of a common ticket never name its requester
 * @param managerIds  managers of the site (K1)
 * @param routedIds   technicians designated by the routing (H4)
 */
public record TicketCreatedV1(UUID ticketId, String reference, OrganizationId organizationId, SiteId siteId,
                              String siteName, boolean common, Actor requester, List<PersonId> managerIds,
                              List<PersonId> routedIds, Instant occurredAt) {
}
