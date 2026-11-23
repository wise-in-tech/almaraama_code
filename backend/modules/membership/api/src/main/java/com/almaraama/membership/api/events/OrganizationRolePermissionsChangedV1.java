package com.almaraama.membership.api.events;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.access.RoleCode;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import java.time.Instant;
import java.util.Set;

/** The ORG_ADMIN changed what a role can do in the organization (C9, ADR-026). Audited (RB-5). */
public record OrganizationRolePermissionsChangedV1(OrganizationId organizationId, RoleCode role, Set<Permission> before,
                                                   Set<Permission> after, Actor actor, Instant occurredAt) {
}
