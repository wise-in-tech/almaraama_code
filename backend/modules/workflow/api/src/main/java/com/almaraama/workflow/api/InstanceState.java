package com.almaraama.workflow.api;

import java.time.Instant;
import java.util.UUID;

/**
 * Where a subject is in its circuit.
 *
 * @param versionId the circuit version the instance runs on (fixed for its life, ADR-036)
 * @param enteredAt when the current step was entered
 */
public record InstanceState(UUID instanceId, UUID versionId, NodeState node, Instant enteredAt) {
}
