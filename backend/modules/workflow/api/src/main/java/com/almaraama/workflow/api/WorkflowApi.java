package com.almaraama.workflow.api;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.i18n.LocalizedText;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/**
 * The circuit engine (ADR-036) for other modules. The caller checks the visibility of its subject (M3) and passes
 * what it knows about the actor in a {@link TransitionContext}; the engine applies C6, the passage's permission and its
 * conditions, moves the token and writes the history (H9). It is called in the caller's transaction.
 */
public interface WorkflowApi {

    /** Subject type of tickets. */
    String TICKET = "TICKET";

    /**
     * Starts the circuit of a new subject: the organization's chosen circuit, else the platform's {@code
     * default-ticket}, on its last published version.
     */
    InstanceState start(String subjectType, UUID subjectId, OrganizationId organizationId, SiteId siteId, Actor actor);

    /** The current step of an instance; {@code NOT_FOUND} for an unknown instance. */
    InstanceState current(UUID instanceId);

    /** The passages the actor may take now, in the circuit's order (H8 read only: none for a mere reader). */
    List<TransitionOption> available(UUID instanceId, TransitionContext context);

    /**
     * Takes a passage. {@code TRANSITION_NOT_AVAILABLE} (not from this step, or not open to the actor), {@code
     * SELF_CLOSE_FORBIDDEN} (C6), {@code REASON_REQUIRED}, {@code COMMENT_REQUIRED}, {@code
     * REQUESTER_CONFIRMATION_REQUIRED}.
     */
    FireResult fire(UUID instanceId, String transitionCode, TransitionContext context, FireInput input);

    /** The history of an instance, oldest first (H9). */
    List<StepRecord> history(UUID instanceId);

    /** The labels of every step of a version for an organization (its own labels first, H5). */
    Map<String, LocalizedText> labels(UUID versionId, OrganizationId organizationId);

    /** The automatic passages due now (auto-close of every organization that enabled it), at most {@code limit}. */
    List<DueTransition> dueAutomaticTransitions(Instant now, int limit);
}
