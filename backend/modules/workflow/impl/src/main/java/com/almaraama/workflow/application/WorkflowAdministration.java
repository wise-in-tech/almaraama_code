package com.almaraama.workflow.application;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.workflow.api.NodeCategory;
import com.almaraama.workflow.api.WorkflowApi;
import com.almaraama.workflow.api.events.WorkflowBindingChangedV1;
import com.almaraama.workflow.api.events.WorkflowVersionPublishedV1;
import com.almaraama.workflow.domain.Workflow;
import com.almaraama.workflow.domain.WorkflowBinding;
import com.almaraama.workflow.domain.WorkflowNode;
import com.almaraama.workflow.domain.WorkflowRepository;
import com.almaraama.workflow.domain.WorkflowTransition;
import com.almaraama.workflow.domain.WorkflowVersion;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayDeque;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.Deque;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * The back-office of the ticket circuits (C12, section 3.5, H5, ADR-036): circuits and versions, publication of a
 * draft loaded by the architect, circuit and options of each client. Platform permissions {@code
 * ticket-workflow:read} and {@code ticket-workflow:configure} only.
 */
@Service
public class WorkflowAdministration {

    private static final Logger LOG = LoggerFactory.getLogger(WorkflowAdministration.class);

    /** A circuit with its versions and the number of organizations bound to it. */
    public record WorkflowSummary(Workflow workflow, List<WorkflowVersion> versions, long organizationsUsing) {
    }

    /** A version with its steps and passages. */
    public record VersionDetail(Workflow workflow, WorkflowVersion version, List<WorkflowNode> nodes,
                                List<WorkflowTransition> transitions, Map<UUID, String> nodeCodes) {
    }

    /** The circuit of a client and its options. */
    public record OrganizationWorkflow(OrganizationId organizationId, WorkflowSummary workflow,
                                       int publishedVersion, boolean chosen, WorkflowSettings settings,
                                       List<WorkflowNode> nodes, long version) {
    }

    private final WorkflowRepository repository;
    private final WorkflowEngine engine;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * @param repository circuit tables
     * @param engine     condition catalogue and definition cache
     * @param events     publication of the audit events
     * @param clock      time of changes
     */
    WorkflowAdministration(WorkflowRepository repository, WorkflowEngine engine, ApplicationEventPublisher events,
                           Clock clock) {
        this.repository = repository;
        this.engine = engine;
        this.events = events;
        this.clock = clock;
    }

    /** The ticket circuits, the platform default first; needs {@code ticket-workflow:read}. */
    @Transactional(readOnly = true)
    public List<WorkflowSummary> list(Perimeter perimeter) {
        perimeter.requireOnPlatform(Permission.TICKET_WORKFLOW_READ);
        return repository.workflows(WorkflowApi.TICKET).stream()
                .sorted(Comparator.comparing((Workflow w) -> !Workflow.DEFAULT_TICKET.equals(w.code()))
                        .thenComparing(Workflow::code))
                .map(this::summary).toList();
    }

    /** A version with its steps and passages; needs {@code ticket-workflow:read}. {@code NOT_FOUND}. */
    @Transactional(readOnly = true)
    public VersionDetail version(Perimeter perimeter, UUID workflowId, int number) {
        perimeter.requireOnPlatform(Permission.TICKET_WORKFLOW_READ);
        return detail(workflowId, number);
    }

    /**
     * Publishes a draft after checking it (see {@link #violations}); the previous published version is retired and
     * open subjects finish on their own version. Needs {@code ticket-workflow:configure}. {@code
     * WORKFLOW_VERSION_PUBLISHED}, {@code WORKFLOW_DEFINITION_INVALID} with each problem.
     */
    @Transactional
    public VersionDetail publish(Perimeter perimeter, UUID workflowId, int number) {
        perimeter.requireOnPlatform(Permission.TICKET_WORKFLOW_CONFIGURE);
        VersionDetail detail = detail(workflowId, number);
        if (!WorkflowVersion.DRAFT.equals(detail.version().status())) {
            throw new BusinessException("WORKFLOW_VERSION_PUBLISHED", ErrorCategory.CONFLICT, "Not a draft");
        }
        List<String> problems = violations(detail.nodes(), detail.transitions());
        if (!problems.isEmpty()) {
            throw new BusinessException("WORKFLOW_DEFINITION_INVALID", ErrorCategory.RULE_VIOLATION,
                    "Circuit refused", problems);
        }
        Instant now = clock.instant();
        for (WorkflowVersion other : repository.versions(workflowId)) {
            if (WorkflowVersion.PUBLISHED.equals(other.status())) {
                other.retire();
                repository.save(other);
                engine.forget(other.id());
            }
        }
        detail.version().publish(now);
        repository.save(detail.version());
        engine.forget(detail.version().id());
        events.publishEvent(new WorkflowVersionPublishedV1(workflowId, detail.workflow().code(), number,
                perimeter.actor(), now));
        LOG.info("Circuit {} version {} published", detail.workflow().code(), number);
        return detail(workflowId, number);
    }

    /** The circuit of a client; without a choice, the platform default without options. Needs {@code ticket-workflow:read}. */
    @Transactional(readOnly = true)
    public OrganizationWorkflow organization(Perimeter perimeter, OrganizationId organizationId) {
        perimeter.requireOnPlatform(Permission.TICKET_WORKFLOW_READ);
        Optional<WorkflowBinding> binding = repository.binding(organizationId, WorkflowApi.TICKET);
        Workflow workflow = binding.flatMap(b -> repository.workflow(b.workflowId()))
                .or(() -> repository.platformWorkflow(Workflow.DEFAULT_TICKET)).orElseThrow();
        WorkflowVersion published = published(workflow.id())
                .orElseThrow(() -> new IllegalStateException("No published version of " + workflow.code()));
        return new OrganizationWorkflow(organizationId, summary(workflow), published.number(), binding.isPresent(),
                binding.map(b -> WorkflowSettings.parse(b.settingsJson())).orElse(WorkflowSettings.none()),
                repository.nodes(published.id()), binding.map(WorkflowBinding::version).orElse(0L));
    }

    /**
     * Chooses the circuit of a client and its options (H5, criterion 19): a new circuit applies to new subjects, the
     * options at once. Needs {@code ticket-workflow:configure}. {@code WORKFLOW_SETTINGS_INVALID} (no published
     * version, not a ticket circuit, another organization's circuit, label of an unknown step), {@code
     * VERSION_CONFLICT}. Audited with the values before and after.
     */
    @Transactional
    public OrganizationWorkflow choose(Perimeter perimeter, OrganizationId organizationId, UUID workflowId,
                                       WorkflowSettings settings, Optional<Long> version) {
        perimeter.requireOnPlatform(Permission.TICKET_WORKFLOW_CONFIGURE);
        Workflow workflow = repository.workflow(workflowId).filter(w -> WorkflowApi.TICKET.equals(w.subjectType()))
                .filter(w -> w.ownerOrganizationId().map(organizationId.value()::equals).orElse(true))
                .orElseThrow(() -> invalidSettings("Not a ticket circuit of this client"));
        WorkflowVersion published = published(workflowId).orElseThrow(() -> invalidSettings("No published version"));
        Set<String> codes = new HashSet<>(repository.nodes(published.id()).stream().map(WorkflowNode::code).toList());
        if (!codes.containsAll(settings.labels().keySet())
                || settings.labels().values().stream().anyMatch(t -> t.longerThan(100))) {
            throw invalidSettings("Label of an unknown step");
        }
        if (settings.autoCloseAfterDays().filter(d -> d < 1 || d > 90).isPresent()) {
            throw invalidSettings("Auto-close days");
        }
        Instant now = clock.instant();
        Optional<WorkflowBinding> current = repository.binding(organizationId, WorkflowApi.TICKET);
        String before = current.map(b -> describe(b.workflowId(), b.settingsJson())).orElse("");
        WorkflowBinding binding;
        if (current.isPresent()) {
            binding = current.get();
            if (version.isPresent() && version.get() != binding.version()) {
                throw new BusinessException("VERSION_CONFLICT", ErrorCategory.CONFLICT, "Changed in the meantime");
            }
            binding.change(workflowId, settings.toJson(), now);
        } else {
            binding = WorkflowBinding.of(organizationId, WorkflowApi.TICKET, workflowId, settings.toJson(), now);
        }
        repository.save(binding);
        events.publishEvent(new WorkflowBindingChangedV1(organizationId, workflowId, before,
                describe(workflowId, settings.toJson()), perimeter.actor(), now));
        LOG.info("Circuit {} chosen for organization {}", workflow.code(), organizationId.value());
        return organization(perimeter, organizationId);
    }

    /**
     * The problems of a draft (ADR-036): one start step of category {@code OPEN}, at least one {@code CLOSED} and one
     * {@code CANCELLED} step, every step reachable, known conditions with valid configurations, known permissions.
     */
    List<String> violations(List<WorkflowNode> nodes, List<WorkflowTransition> transitions) {
        List<String> problems = new ArrayList<>();
        Set<UUID> entered = new HashSet<>(transitions.stream().map(WorkflowTransition::toNodeId).toList());
        List<WorkflowNode> starts = nodes.stream()
                .filter(n -> n.category() == NodeCategory.OPEN && !entered.contains(n.id())).toList();
        if (starts.size() != 1) {
            problems.add("start");
        }
        if (nodes.stream().noneMatch(n -> n.category() == NodeCategory.CLOSED)) {
            problems.add("closed");
        }
        if (nodes.stream().noneMatch(n -> n.category() == NodeCategory.CANCELLED)) {
            problems.add("cancelled");
        }
        if (starts.size() == 1) {
            Set<UUID> reached = new HashSet<>();
            Deque<UUID> queue = new ArrayDeque<>(List.of(starts.getFirst().id()));
            while (!queue.isEmpty()) {
                UUID node = queue.pop();
                if (reached.add(node)) {
                    transitions.stream().filter(t -> t.fromNodeId().equals(node))
                            .forEach(t -> queue.push(t.toNodeId()));
                }
            }
            nodes.stream().filter(n -> !reached.contains(n.id())).forEach(n -> problems.add("unreachable:" + n.code()));
        }
        for (WorkflowTransition transition : transitions) {
            transition.permission().filter(p -> Permission.fromCode(p).isEmpty())
                    .ifPresent(p -> problems.add("permission:" + transition.code()));
            List<Map<String, Object>> refs = Json.array(transition.guardsJson());
            if (refs == null) {
                problems.add("guards:" + transition.code());
                continue;
            }
            for (Map<String, Object> ref : refs) {
                String code = String.valueOf(ref.get("code"));
                @SuppressWarnings("unchecked")
                Map<String, Object> config = ref.get("config") instanceof Map<?, ?> m ? (Map<String, Object>) m
                        : Map.of();
                if (engine.guardOf(code).filter(g -> g.validConfig(config)).isEmpty()) {
                    problems.add("guard:" + transition.code() + ":" + code);
                }
            }
        }
        return problems;
    }

    /** A circuit with its versions. */
    private WorkflowSummary summary(Workflow workflow) {
        return new WorkflowSummary(workflow, repository.versions(workflow.id()), repository.bindingsOf(workflow.id()));
    }

    /** The last published version of a circuit. */
    private Optional<WorkflowVersion> published(UUID workflowId) {
        return repository.versions(workflowId).stream().filter(v -> WorkflowVersion.PUBLISHED.equals(v.status()))
                .max(Comparator.comparingInt(WorkflowVersion::number));
    }

    /** A version with its steps and passages, or {@code NOT_FOUND}. */
    private VersionDetail detail(UUID workflowId, int number) {
        Workflow workflow = repository.workflow(workflowId).orElseThrow(WorkflowAdministration::notFound);
        WorkflowVersion version = repository.versions(workflowId).stream().filter(v -> v.number() == number)
                .findFirst().orElseThrow(WorkflowAdministration::notFound);
        List<WorkflowNode> nodes = repository.nodes(version.id());
        Map<UUID, String> codes = new java.util.HashMap<>();
        nodes.forEach(n -> codes.put(n.id(), n.code()));
        return new VersionDetail(workflow, version, nodes, repository.transitions(version.id()), codes);
    }

    /** The audited description of a choice: circuit code and options. */
    private String describe(UUID workflowId, String settingsJson) {
        String code = repository.workflow(workflowId).map(Workflow::code).orElse(workflowId.toString());
        return Json.write(Map.of("workflow", code, "settings", Json.object(settingsJson)));
    }

    /** The error of an invalid choice. */
    private static BusinessException invalidSettings(String message) {
        return new BusinessException("WORKFLOW_SETTINGS_INVALID", ErrorCategory.RULE_VIOLATION, message);
    }

    /** Unknown circuit or version. */
    private static BusinessException notFound() {
        return new BusinessException("NOT_FOUND", ErrorCategory.NOT_FOUND, "No circuit");
    }
}
