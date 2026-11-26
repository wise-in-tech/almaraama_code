package com.almaraama.workflow.api;

import java.util.List;

/**
 * A passage the actor may take now, with what it requires (ADR-036).
 *
 * @param reasons                   allowed reasons when a reason is required
 * @param commentRequired           a comment is always required
 * @param commentRequiredForReasons reasons that need a comment ({@code OTHER})
 */
public record TransitionOption(String code, NodeState to, boolean reasonRequired, List<String> reasons,
                               boolean commentRequired, List<String> commentRequiredForReasons) {
}
