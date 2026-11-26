package com.almaraama.workflow.api;

import com.almaraama.shared.actor.Actor;
import java.time.Instant;
import java.util.Optional;

/**
 * One entry of the circuit's history (H9): the start or a passage.
 *
 * @param transitionCode empty for the start of the circuit
 */
public record StepRecord(Instant at, Optional<String> transitionCode, Optional<NodeState> from, NodeState to,
                         Actor actor, Optional<String> reason, Optional<String> comment) {
}
