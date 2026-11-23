package com.almaraama.membership.api.events;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** A permission was added to one person or removed for them only (C9, ADR-026). Audited (RB-5). */
public record PermissionAdjustedV1(UUID adjustmentId, PersonId personId, OrganizationId organizationId,
                                   ScopeType scopeType, Optional<SiteId> siteId, Permission permission, String effect,
                                   Actor actor, Instant occurredAt) {
}
