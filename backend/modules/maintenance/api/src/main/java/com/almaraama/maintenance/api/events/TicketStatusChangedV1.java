package com.almaraama.maintenance.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * A ticket took a passage of its circuit (H5, H6, H7, K1, K3). Steps are given by code and fixed category so that the
 * consumers stay right whatever the client's circuit. Versioned immutable event (CF-5).
 *
 * @param fromCategory  category of the step left
 * @param toCategory    category of the step entered ({@code CLOSED}, {@code CANCELLED} end the ticket)
 * @param requesterId   the requester when a person (K3)
 * @param managerIds    managers of the site (K1)
 * @param technicianIds assigned technicians
 */
public record TicketStatusChangedV1(UUID ticketId, String reference, OrganizationId organizationId, SiteId siteId,
                                    String siteName, String transition, String fromNode, String fromCategory,
                                    String toNode, String toCategory, Actor actor, Optional<PersonId> requesterId,
                                    List<PersonId> managerIds, List<PersonId> technicianIds, Instant occurredAt) {
}
