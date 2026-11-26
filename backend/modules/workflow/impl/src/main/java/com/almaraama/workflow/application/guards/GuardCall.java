package com.almaraama.workflow.application.guards;

import com.almaraama.workflow.api.FireInput;
import com.almaraama.workflow.api.TransitionContext;
import com.almaraama.workflow.application.WorkflowSettings;
import com.almaraama.workflow.domain.WorkflowEvent;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;

/**
 * What a condition may look at.
 *
 * @param input     what was typed; {@link FireInput#none()} while listing the available passages
 * @param config    the condition's configuration in the circuit
 * @param settings  the client's options, read now (H5)
 * @param enteredAt when the instance entered its current step
 * @param history   the instance's history, loaded only when a condition needs it
 */
public record GuardCall(TransitionContext context, FireInput input, Map<String, Object> config,
                        WorkflowSettings settings, Instant enteredAt, Supplier<List<WorkflowEvent>> history) {
}
