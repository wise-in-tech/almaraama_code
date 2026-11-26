package com.almaraama.workflow.domain;

import com.almaraama.shared.id.OrganizationId;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Out port: definitions, bindings and runs of circuits (ADR-036). Nothing is deleted (M2). */
public interface WorkflowRepository {

    /** Every circuit of a subject type. */
    List<Workflow> workflows(String subjectType);

    /** A circuit. */
    Optional<Workflow> workflow(UUID id);

    /** The platform circuit with this code. */
    Optional<Workflow> platformWorkflow(String code);

    /** The versions of a circuit, by number. */
    List<WorkflowVersion> versions(UUID workflowId);

    /** A version. */
    Optional<WorkflowVersion> version(UUID id);

    /** Stores a version. */
    WorkflowVersion save(WorkflowVersion version);

    /** The steps of a version. */
    List<WorkflowNode> nodes(UUID versionId);

    /** The steps with an {@code autoClose} configuration, in every version. */
    List<WorkflowNode> autoCloseNodes();

    /** The passages of a version, by sort order. */
    List<WorkflowTransition> transitions(UUID versionId);

    /** The binding of an organization for a subject type. */
    Optional<WorkflowBinding> binding(OrganizationId organization, String subjectType);

    /** Every binding whose options are not empty (auto-close job). */
    List<WorkflowBinding> bindingsWithSettings();

    /** The number of organizations bound to each circuit. */
    long bindingsOf(UUID workflowId);

    /** Stores a binding. */
    WorkflowBinding save(WorkflowBinding binding);

    /** An instance. */
    Optional<WorkflowInstance> instance(UUID id);

    /** Stores an instance. */
    WorkflowInstance save(WorkflowInstance instance);

    /** The active token of an instance. */
    Optional<WorkflowToken> activeToken(UUID instanceId);

    /** Stores a token. */
    WorkflowToken save(WorkflowToken token);

    /** Stores a token and writes it at once, before the next token enters (one active token per instance). */
    WorkflowToken saveNow(WorkflowToken token);

    /** Active tokens of the organization in these steps entered before {@code before}, oldest first. */
    List<WorkflowToken> tokensWaiting(OrganizationId organization, Collection<UUID> nodeIds, Instant before,
                                      int limit);

    /** The history of an instance, oldest first. */
    List<WorkflowEvent> events(UUID instanceId);

    /** Appends a history entry. */
    WorkflowEvent save(WorkflowEvent event);
}
