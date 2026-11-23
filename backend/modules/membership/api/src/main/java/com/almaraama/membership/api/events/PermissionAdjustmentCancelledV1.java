package com.almaraama.membership.api.events;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import java.time.Instant;
import java.util.UUID;

/** A person-level adjustment was cancelled: the role's permissions apply again (ADR-026). Audited (RB-5). */
public record PermissionAdjustmentCancelledV1(UUID adjustmentId, PersonId personId, OrganizationId organizationId,
                                              Permission permission, String effect, Actor actor, Instant occurredAt) {
}
