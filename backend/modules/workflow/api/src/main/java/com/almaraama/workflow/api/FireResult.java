package com.almaraama.workflow.api;

import java.time.Instant;

/**
 * The passage taken.
 *
 * @param moved false for a passage that stays on its step ({@code confirm_resolution}): only the history changes
 */
public record FireResult(String transitionCode, NodeState from, NodeState to, Instant at, boolean moved) {
}
