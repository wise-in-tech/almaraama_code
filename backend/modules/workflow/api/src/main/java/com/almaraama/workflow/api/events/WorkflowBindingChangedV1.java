package com.almaraama.workflow.api.events;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import java.time.Instant;
import java.util.UUID;

/**
 * The circuit or the options of a client changed (H5, C12, ADR-036). Audited with the values before and after, as
 * JSON text (no personal data).
 *
 * @param before circuit code and options before, or empty text for the first choice
 * @param after  circuit code and options after
 */
public record WorkflowBindingChangedV1(OrganizationId organizationId, UUID workflowId, String before, String after,
                                       Actor actor, Instant occurredAt) {
}
