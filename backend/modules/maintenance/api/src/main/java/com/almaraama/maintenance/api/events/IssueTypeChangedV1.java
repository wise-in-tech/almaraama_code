package com.almaraama.maintenance.api.events;

import com.almaraama.shared.actor.Actor;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * An issue category or type was created or changed (H2, M7): by the platform ({@code organizationId} empty) or an
 * organization. Versioned (CF-5).
 *
 * @param kind {@code CATEGORY} or {@code TYPE}
 */
public record IssueTypeChangedV1(UUID id, String kind, String code, Optional<UUID> organizationId, boolean active,
                                 Actor actor, Instant occurredAt) {
}
