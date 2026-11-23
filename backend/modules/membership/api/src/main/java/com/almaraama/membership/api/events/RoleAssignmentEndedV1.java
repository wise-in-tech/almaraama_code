package com.almaraama.membership.api.events;

import com.almaraama.shared.access.RoleCode;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** A role was taken away from a person (ADR-026). Audited (RB-5). */
public record RoleAssignmentEndedV1(UUID assignmentId, PersonId personId, RoleCode role, ScopeType scopeType,
                                    Optional<OrganizationId> organizationId, Optional<SiteId> siteId, Actor actor,
                                    Instant occurredAt) {
}
