package com.almaraama.workflow.adapters.out.persistence;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.workflow.domain.Workflow;
import com.almaraama.workflow.domain.WorkflowBinding;
import com.almaraama.workflow.domain.WorkflowEvent;
import com.almaraama.workflow.domain.WorkflowInstance;
import com.almaraama.workflow.domain.WorkflowNode;
import com.almaraama.workflow.domain.WorkflowRepository;
import com.almaraama.workflow.domain.WorkflowToken;
import com.almaraama.workflow.domain.WorkflowTransition;
import com.almaraama.workflow.domain.WorkflowVersion;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.stereotype.Component;

/** {@link WorkflowRepository} on the {@code workflow} tables. */
@Component
class JpaWorkflowRepository implements WorkflowRepository {

    private final SpringDataWorkflows workflows;
    private final SpringDataWorkflowVersions versions;
    private final SpringDataWorkflowNodes nodes;
    private final SpringDataWorkflowTransitions transitions;
    private final SpringDataWorkflowBindings bindings;
    private final SpringDataWorkflowInstances instances;
    private final SpringDataWorkflowTokens tokens;
    private final SpringDataWorkflowEvents events;

    /**
     * @param workflows   circuits
     * @param versions    versions
     * @param nodes       steps
     * @param transitions passages
     * @param bindings    bindings
     * @param instances   runs
     * @param tokens      positions
     * @param events      history
     */
    JpaWorkflowRepository(SpringDataWorkflows workflows, SpringDataWorkflowVersions versions,
                          SpringDataWorkflowNodes nodes, SpringDataWorkflowTransitions transitions,
                          SpringDataWorkflowBindings bindings, SpringDataWorkflowInstances instances,
                          SpringDataWorkflowTokens tokens, SpringDataWorkflowEvents events) {
        this.workflows = workflows;
        this.versions = versions;
        this.nodes = nodes;
        this.transitions = transitions;
        this.bindings = bindings;
        this.instances = instances;
        this.tokens = tokens;
        this.events = events;
    }

    /** {@inheritDoc} */
    @Override
    public List<Workflow> workflows(String subjectType) {
        return workflows.findBySubjectType(subjectType);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Workflow> workflow(UUID id) {
        return workflows.findById(id);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<Workflow> platformWorkflow(String code) {
        return workflows.platform(code);
    }

    /** {@inheritDoc} */
    @Override
    public List<WorkflowVersion> versions(UUID workflowId) {
        return versions.findByWorkflowIdOrderByNumberAsc(workflowId);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<WorkflowVersion> version(UUID id) {
        return versions.findById(id);
    }

    /** {@inheritDoc} */
    @Override
    public WorkflowVersion save(WorkflowVersion version) {
        return versions.save(version);
    }

    /** {@inheritDoc} */
    @Override
    public List<WorkflowNode> nodes(UUID versionId) {
        return nodes.findByVersionId(versionId);
    }

    /** {@inheritDoc} */
    @Override
    public List<WorkflowNode> autoCloseNodes() {
        return nodes.autoClose();
    }

    /** {@inheritDoc} */
    @Override
    public List<WorkflowTransition> transitions(UUID versionId) {
        return transitions.findByVersionIdOrderBySortOrderAscCodeAsc(versionId);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<WorkflowBinding> binding(OrganizationId organization, String subjectType) {
        return bindings.ofOrganization(organization.value(), subjectType);
    }

    /** {@inheritDoc} */
    @Override
    public List<WorkflowBinding> bindingsWithSettings() {
        return bindings.withAutoClose();
    }

    /** {@inheritDoc} */
    @Override
    public long bindingsOf(UUID workflowId) {
        return bindings.countByWorkflowId(workflowId);
    }

    /** {@inheritDoc} */
    @Override
    public WorkflowBinding save(WorkflowBinding binding) {
        return bindings.save(binding);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<WorkflowInstance> instance(UUID id) {
        return instances.findById(id);
    }

    /** {@inheritDoc} */
    @Override
    public WorkflowInstance save(WorkflowInstance instance) {
        return instances.save(instance);
    }

    /** {@inheritDoc} */
    @Override
    public Optional<WorkflowToken> activeToken(UUID instanceId) {
        return tokens.findByInstanceIdAndActiveTrue(instanceId);
    }

    /** {@inheritDoc} */
    @Override
    public WorkflowToken save(WorkflowToken token) {
        return tokens.save(token);
    }

    /** {@inheritDoc} */
    @Override
    public WorkflowToken saveNow(WorkflowToken token) {
        return tokens.saveAndFlush(token);
    }

    /** {@inheritDoc} */
    @Override
    public List<WorkflowToken> tokensWaiting(OrganizationId organization, Collection<UUID> nodeIds, Instant before,
                                             int limit) {
        return nodeIds.isEmpty() || limit <= 0 ? List.of()
                : tokens.waiting(organization.value(), nodeIds, before, Limit.of(limit));
    }

    /** {@inheritDoc} */
    @Override
    public List<WorkflowEvent> events(UUID instanceId) {
        return events.findByInstanceIdOrderByOccurredAtAsc(instanceId);
    }

    /** {@inheritDoc} */
    @Override
    public WorkflowEvent save(WorkflowEvent event) {
        return events.save(event);
    }
}
