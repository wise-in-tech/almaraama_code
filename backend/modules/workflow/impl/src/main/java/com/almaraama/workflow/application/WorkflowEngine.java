package com.almaraama.workflow.application;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.i18n.LocalizedText;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.workflow.api.DueTransition;
import com.almaraama.workflow.api.FireInput;
import com.almaraama.workflow.api.FireResult;
import com.almaraama.workflow.api.InstanceState;
import com.almaraama.workflow.api.NodeCategory;
import com.almaraama.workflow.api.NodeState;
import com.almaraama.workflow.api.StepRecord;
import com.almaraama.workflow.api.TransitionContext;
import com.almaraama.workflow.api.TransitionOption;
import com.almaraama.workflow.api.WorkflowApi;
import com.almaraama.workflow.application.guards.Guard;
import com.almaraama.workflow.application.guards.GuardCall;
import com.almaraama.workflow.domain.Workflow;
import com.almaraama.workflow.domain.WorkflowBinding;
import com.almaraama.workflow.domain.WorkflowEvent;
import com.almaraama.workflow.domain.WorkflowInstance;
import com.almaraama.workflow.domain.WorkflowNode;
import com.almaraama.workflow.domain.WorkflowRepository;
import com.almaraama.workflow.domain.WorkflowToken;
import com.almaraama.workflow.domain.WorkflowTransition;
import com.almaraama.workflow.domain.WorkflowVersion;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;
import java.util.function.Supplier;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The in-house circuit engine (ADR-036, architecture section 08): open to new conditions, closed to modification.
 * Before any condition it applies the invariants that no circuit can change: C6 (nobody closes a subject they asked
 * for). The caller has applied M3 (visibility) before calling.
 */
@Service
public class WorkflowEngine implements WorkflowApi {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowEngine.class);

    private final WorkflowRepository repository;
    private final Map<String, Guard> guards;
    private final Clock clock;
    private final Map<UUID, Definition> published = new ConcurrentHashMap<>();

    /**
     * @param repository circuit tables
     * @param guards     the condition catalogue (every {@link Guard} component)
     * @param clock      time of the passages
     */
    WorkflowEngine(WorkflowRepository repository, List<Guard> guards, Clock clock) {
        this.repository = repository;
        this.guards = guards.stream().collect(Collectors.toMap(Guard::code, Function.identity()));
        this.clock = clock;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public InstanceState start(String subjectType, UUID subjectId, OrganizationId organizationId, SiteId siteId,
                               Actor actor) {
        Workflow workflow = repository.binding(organizationId, subjectType)
                .flatMap(b -> repository.workflow(b.workflowId()))
                .or(() -> repository.platformWorkflow(Workflow.DEFAULT_TICKET))
                .orElseThrow(() -> new IllegalStateException("No circuit for " + subjectType));
        WorkflowVersion version = repository.versions(workflow.id()).stream()
                .filter(v -> WorkflowVersion.PUBLISHED.equals(v.status()))
                .max(Comparator.comparingInt(WorkflowVersion::number))
                .orElseThrow(() -> new IllegalStateException("No published version of " + workflow.code()));
        Definition definition = definition(version.id());
        WorkflowNode start = definition.start()
                .orElseThrow(() -> new IllegalStateException("No start step in " + workflow.code()));
        Instant now = clock.instant();
        WorkflowInstance instance = repository.save(WorkflowInstance.start(version.id(), subjectType, subjectId,
                organizationId, siteId, now));
        WorkflowToken token = repository.save(WorkflowToken.enter(instance.id(), start, now));
        repository.save(WorkflowEvent.of(instance.id(), Optional.empty(), Optional.empty(), start.code(), actor, "{}",
                now));
        LOG.debug("Circuit {} v{} started for {} {}", workflow.code(), version.number(), subjectType, subjectId);
        return new InstanceState(instance.id(), version.id(), state(start, settings(organizationId)),
                token.enteredAt());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public InstanceState current(UUID instanceId) {
        WorkflowInstance instance = instance(instanceId);
        WorkflowToken token = token(instanceId);
        WorkflowNode node = definition(instance.versionId()).nodeById().get(token.nodeId());
        return new InstanceState(instanceId, instance.versionId(), state(node, settings(instance.organizationId())),
                token.enteredAt());
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<TransitionOption> available(UUID instanceId, TransitionContext context) {
        WorkflowInstance instance = instance(instanceId);
        WorkflowToken token = token(instanceId);
        Definition definition = definition(instance.versionId());
        WorkflowSettings settings = settings(instance.organizationId());
        List<TransitionOption> options = new ArrayList<>();
        Set<String> seen = new java.util.HashSet<>();
        for (WorkflowTransition transition : definition.from(token.nodeId())) {
            WorkflowNode target = definition.nodeById().get(transition.toNodeId());
            if (seen.contains(transition.code()) || !open(transition, target, context, settings, token, instanceId)) {
                continue;
            }
            seen.add(transition.code());
            options.add(option(transition, target, settings));
        }
        return options;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional
    public FireResult fire(UUID instanceId, String transitionCode, TransitionContext context, FireInput input) {
        WorkflowInstance instance = instance(instanceId);
        WorkflowToken token = token(instanceId);
        Definition definition = definition(instance.versionId());
        WorkflowSettings settings = settings(instance.organizationId());
        WorkflowTransition transition = definition.from(token.nodeId()).stream()
                .filter(t -> t.code().equals(transitionCode)).findFirst()
                .orElseThrow(WorkflowEngine::notAvailable);
        WorkflowNode from = definition.nodeById().get(token.nodeId());
        WorkflowNode to = definition.nodeById().get(transition.toNodeId());
        if (to.category() == NodeCategory.CLOSED && context.actorIsRequester()) {
            LOG.debug("C6: {} may not close what they asked for ({})", context.actor().id(), instanceId);
            throw new BusinessException("SELF_CLOSE_FORBIDDEN", ErrorCategory.RULE_VIOLATION, "C6");
        }
        if (transition.permission().filter(p -> !context.hasPermission().test(p)).isPresent()) {
            LOG.debug("Passage {} refused to {}: permission {} missing", transitionCode, context.actor().id(),
                    transition.permission().get());
            throw notAvailable();
        }
        Supplier<List<WorkflowEvent>> history = () -> repository.events(instanceId);
        for (Map<String, Object> ref : refs(transition)) {
            guard(ref).check(new GuardCall(context, input, config(ref), settings, token.enteredAt(), history));
        }
        Instant now = clock.instant();
        boolean moved = !from.id().equals(to.id());
        if (moved) {
            token.leave(now);
            repository.saveNow(token);
            repository.save(WorkflowToken.enter(instanceId, to, now));
            if (to.category().terminal()) {
                instance.end(now);
            }
        }
        repository.save(instance);
        Map<String, Object> payload = new LinkedHashMap<>();
        input.reason().ifPresent(r -> payload.put("reason", r));
        input.comment().map(String::strip).filter(c -> !c.isEmpty()).ifPresent(c -> payload.put("comment", c));
        repository.save(WorkflowEvent.of(instanceId, Optional.of(transition), Optional.of(from.code()), to.code(),
                context.actor(), Json.write(payload), now));
        LOG.debug("Passage {} taken on {}: {} -> {}", transitionCode, instanceId, from.code(), to.code());
        return new FireResult(transitionCode, state(from, settings), state(to, settings), now, moved);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<StepRecord> history(UUID instanceId) {
        WorkflowInstance instance = instance(instanceId);
        Definition definition = definition(instance.versionId());
        WorkflowSettings settings = settings(instance.organizationId());
        List<StepRecord> steps = new ArrayList<>();
        for (WorkflowEvent event : repository.events(instanceId)) {
            Map<String, Object> payload = Json.object(event.payloadJson());
            steps.add(new StepRecord(event.occurredAt(), event.transitionCode(),
                    event.fromNodeCode().flatMap(definition::node).map(n -> state(n, settings)),
                    definition.node(event.toNodeCode()).map(n -> state(n, settings))
                            .orElseGet(() -> new NodeState(event.toNodeCode(), NodeCategory.OPEN,
                                    LocalizedText.same(event.toNodeCode()))),
                    event.actor(), Optional.ofNullable((String) payload.get("reason")),
                    Optional.ofNullable((String) payload.get("comment"))));
        }
        return steps;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Map<String, LocalizedText> labels(UUID versionId, OrganizationId organizationId) {
        WorkflowSettings settings = settings(organizationId);
        Map<String, LocalizedText> labels = new LinkedHashMap<>();
        for (WorkflowNode node : definition(versionId).nodes()) {
            labels.put(node.code(), state(node, settings).label());
        }
        return labels;
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<DueTransition> dueAutomaticTransitions(Instant now, int limit) {
        Map<UUID, String> autoNodes = new LinkedHashMap<>();
        for (WorkflowNode node : repository.autoCloseNodes()) {
            Object auto = Json.object(node.configJson()).get("autoClose");
            if (auto instanceof Map<?, ?> config && config.get("transition") != null) {
                autoNodes.put(node.id(), String.valueOf(config.get("transition")));
            }
        }
        if (autoNodes.isEmpty()) {
            return List.of();
        }
        List<DueTransition> due = new ArrayList<>();
        for (WorkflowBinding binding : repository.bindingsWithSettings()) {
            Optional<Integer> days = WorkflowSettings.parse(binding.settingsJson()).autoCloseAfterDays();
            if (days.isEmpty() || due.size() >= limit) {
                continue;
            }
            Instant before = now.minus(Duration.ofDays(days.get()));
            for (WorkflowToken token : repository.tokensWaiting(binding.organizationId(), autoNodes.keySet(), before,
                    limit - due.size())) {
                repository.instance(token.instanceId()).ifPresent(i -> due.add(new DueTransition(i.id(),
                        i.subjectType(), i.subjectId(), autoNodes.get(token.nodeId()))));
            }
        }
        return due;
    }

    // ------------------------------------------------------------------------------------------- internals

    /** Whether a passage may be offered: C6, permission, then the conditions that need no input. */
    private boolean open(WorkflowTransition transition, WorkflowNode target, TransitionContext context,
                         WorkflowSettings settings, WorkflowToken token, UUID instanceId) {
        if (target.category() == NodeCategory.CLOSED && context.actorIsRequester()) {
            return false;
        }
        if (transition.permission().filter(p -> !context.hasPermission().test(p)).isPresent()) {
            return false;
        }
        Supplier<List<WorkflowEvent>> history = () -> repository.events(instanceId);
        for (Map<String, Object> ref : refs(transition)) {
            if (!guard(ref).allows(new GuardCall(context, FireInput.none(), config(ref), settings, token.enteredAt(),
                    history))) {
                return false;
            }
        }
        return true;
    }

    /** What a passage requires, read from its conditions. */
    private TransitionOption option(WorkflowTransition transition, WorkflowNode target, WorkflowSettings settings) {
        boolean reasonRequired = false;
        List<String> reasons = List.of();
        List<String> commentFor = List.of();
        boolean commentRequired = false;
        for (Map<String, Object> ref : refs(transition)) {
            switch (String.valueOf(ref.get("code"))) {
                case "ReasonRequired" -> {
                    reasonRequired = true;
                    reasons = strings(config(ref).get("reasons"));
                    commentFor = strings(config(ref).get("commentRequiredFor"));
                }
                case "CommentRequired" -> commentRequired = true;
                default -> {
                }
            }
        }
        return new TransitionOption(transition.code(), state(target, settings), reasonRequired, reasons,
                commentRequired, commentFor);
    }

    /** The step as other modules see it, with the client's label first (H5). */
    private static NodeState state(WorkflowNode node, WorkflowSettings settings) {
        LocalizedText label = Optional.ofNullable(settings.labels().get(node.code()))
                .map(own -> new LocalizedText(own.ar() != null ? own.ar() : node.label().ar(),
                        own.en() != null ? own.en() : node.label().en(),
                        own.fr() != null ? own.fr() : node.label().fr()))
                .orElse(node.label());
        return new NodeState(node.code(), node.category(), label);
    }

    /** The options of the organization, read now (H5, criterion 19). */
    WorkflowSettings settings(OrganizationId organization) {
        return repository.binding(organization, TICKET).map(b -> WorkflowSettings.parse(b.settingsJson()))
                .orElse(WorkflowSettings.none());
    }

    /** A version's definition, kept in memory once published (frozen, ADR-036). */
    Definition definition(UUID versionId) {
        Definition cached = published.get(versionId);
        if (cached != null) {
            return cached;
        }
        WorkflowVersion version = repository.version(versionId)
                .orElseThrow(() -> new IllegalStateException("Unknown version " + versionId));
        Definition definition = Definition.of(version, repository.nodes(versionId), repository.transitions(versionId));
        if (!WorkflowVersion.DRAFT.equals(version.status())) {
            published.put(versionId, definition);
        }
        return definition;
    }

    /** Forgets a cached definition (after a publication changes its status). */
    void forget(UUID versionId) {
        published.remove(versionId);
    }

    /** The condition references of a passage. */
    private static List<Map<String, Object>> refs(WorkflowTransition transition) {
        List<Map<String, Object>> refs = Json.array(transition.guardsJson());
        return refs == null ? List.of() : refs;
    }

    /** The configuration of a reference. */
    @SuppressWarnings("unchecked")
    private static Map<String, Object> config(Map<String, Object> ref) {
        return ref.get("config") instanceof Map<?, ?> m ? (Map<String, Object>) m : Map.of();
    }

    /** The strings of a configuration list. */
    private static List<String> strings(Object value) {
        return value instanceof java.util.Collection<?> c ? c.stream().map(String::valueOf).toList() : List.of();
    }

    /** The condition of a reference; an unknown code is a broken circuit (refused at publication). */
    private Guard guard(Map<String, Object> ref) {
        Guard guard = guards.get(String.valueOf(ref.get("code")));
        if (guard == null) {
            throw new IllegalStateException("Unknown condition " + ref.get("code"));
        }
        return guard;
    }

    /** Whether the catalogue knows a condition code (publication check). */
    boolean knows(String code) {
        return guards.containsKey(code);
    }

    /** The condition of a code, if known. */
    Optional<Guard> guardOf(String code) {
        return Optional.ofNullable(guards.get(code));
    }

    /** The instance, or {@code NOT_FOUND}. */
    private WorkflowInstance instance(UUID id) {
        return repository.instance(id)
                .orElseThrow(() -> new BusinessException("NOT_FOUND", ErrorCategory.NOT_FOUND, "No instance"));
    }

    /** The active token of an instance. */
    private WorkflowToken token(UUID instanceId) {
        return repository.activeToken(instanceId)
                .orElseThrow(() -> new IllegalStateException("Instance without active token " + instanceId));
    }

    /** The error of a passage that is not open. */
    private static BusinessException notAvailable() {
        return new BusinessException("TRANSITION_NOT_AVAILABLE", ErrorCategory.CONFLICT, "Passage not open");
    }
}
