package com.almaraama.workflow.api.events;

import com.almaraama.shared.actor.Actor;
import java.time.Instant;
import java.util.UUID;

/** A circuit version was published from the back-office (ADR-036); the previous one is retired. Versioned (CF-5). */
public record WorkflowVersionPublishedV1(UUID workflowId, String workflowCode, int number, Actor actor,
                                         Instant occurredAt) {
}
