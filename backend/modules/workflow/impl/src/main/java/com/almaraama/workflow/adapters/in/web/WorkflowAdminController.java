package com.almaraama.workflow.adapters.in.web;

import com.almaraama.shared.i18n.LocalizedText;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.workflow.api.NodeCategory;
import com.almaraama.workflow.application.Json;
import com.almaraama.workflow.application.WorkflowAdministration;
import com.almaraama.workflow.application.WorkflowAdministration.OrganizationWorkflow;
import com.almaraama.workflow.application.WorkflowAdministration.VersionDetail;
import com.almaraama.workflow.application.WorkflowAdministration.WorkflowSummary;
import com.almaraama.workflow.application.WorkflowSettings;
import com.almaraama.workflow.domain.WorkflowNode;
import com.almaraama.workflow.domain.WorkflowVersion;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RestController;

/** Back-office of the ticket circuits (contract tag {@code workflow}, H5, C12, ADR-036). */
@RestController
class WorkflowAdminController {

    private final WorkflowAdministration administration;

    /** @param administration circuits and client choices */
    WorkflowAdminController(WorkflowAdministration administration) {
        this.administration = administration;
    }

    /** A text in every language (contract {@code LocalizedText}). */
    record Text(String ar, String en, String fr) {
        /** From the shared value type. */
        static Text of(LocalizedText t) {
            return new Text(t.ar(), t.en(), t.fr());
        }
    }

    /** A text typed by an administrator (contract {@code LocalizedTextInput}). */
    record TextInput(@Size(min = 1, max = 100) String ar, @Size(min = 1, max = 100) String en,
                     @Size(min = 1, max = 100) String fr) {
    }

    /** A version (contract {@code WorkflowVersionSummary}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record VersionSummary(int number, String status, Instant publishedAt) {
        /** From the version. */
        static VersionSummary of(WorkflowVersion v) {
            return new VersionSummary(v.number(), v.status(), v.publishedAt().orElse(null));
        }
    }

    /** A circuit (contract {@code WorkflowSummary}). */
    record Summary(UUID id, String code, String name, String ownerScope, String subjectType,
                   List<VersionSummary> versions, long organizationsUsing) {
        /** From the use case's summary. */
        static Summary of(WorkflowSummary s) {
            return new Summary(s.workflow().id(), s.workflow().code(), s.workflow().name(), s.workflow().ownerScope(),
                    s.workflow().subjectType(), s.versions().stream().map(VersionSummary::of).toList(),
                    s.organizationsUsing());
        }
    }

    /** A step (contract {@code WorkflowNode}). */
    record Node(String code, String nodeType, NodeCategory category, Text label, Map<String, Object> config) {
        /** From the step. */
        static Node of(WorkflowNode n) {
            return new Node(n.code(), n.nodeType(), n.category(), Text.of(n.label()), Json.object(n.configJson()));
        }
    }

    /** A condition reference (contract {@code WorkflowComponentRef}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ComponentRef(String code, Map<String, Object> config) {
    }

    /** A passage (contract {@code WorkflowTransition}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Transition(String code, String from, String to, String permission, List<ComponentRef> guards,
                      int sortOrder) {
    }

    /** A version with its steps and passages (contract {@code WorkflowVersionDetail}). */
    record Detail(UUID workflowId, String code, String name, VersionSummary version, List<Node> nodes,
                  List<Transition> transitions) {
        /** From the use case's detail. */
        @SuppressWarnings("unchecked")
        static Detail of(VersionDetail d) {
            List<Transition> transitions = d.transitions().stream().map(t -> {
                List<Map<String, Object>> refs = Optional.ofNullable(Json.array(t.guardsJson())).orElse(List.of());
                return new Transition(t.code(), d.nodeCodes().get(t.fromNodeId()), d.nodeCodes().get(t.toNodeId()),
                        t.permission().orElse(null), refs.stream().map(r -> new ComponentRef(String.valueOf(r.get("code")),
                        r.get("config") instanceof Map<?, ?> m ? (Map<String, Object>) m : null)).toList(),
                        t.sortOrder());
            }).toList();
            return new Detail(d.workflow().id(), d.workflow().code(), d.workflow().name(),
                    VersionSummary.of(d.version()), d.nodes().stream().map(Node::of).toList(), transitions);
        }
    }

    /** Options of a client (contract {@code WorkflowSettings}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record Settings(@Min(1) @Max(90) Integer autoCloseAfterDays, @NotNull Boolean requesterValidation,
                    @NotNull Map<String, @Valid TextInput> labels) {
        /** From the use case's options. */
        static Settings of(WorkflowSettings s) {
            Map<String, TextInput> labels = new LinkedHashMap<>();
            s.labels().forEach((code, t) -> labels.put(code, new TextInput(t.ar(), t.en(), t.fr())));
            return new Settings(s.autoCloseAfterDays().orElse(null), s.requesterValidation(), labels);
        }

        /** To the use case's options. */
        WorkflowSettings toSettings() {
            Map<String, LocalizedText> texts = new LinkedHashMap<>();
            labels.forEach((code, t) -> texts.put(code, new LocalizedText(t.ar(), t.en(), t.fr())));
            return new WorkflowSettings(Optional.ofNullable(autoCloseAfterDays), requesterValidation, texts);
        }
    }

    /** A client's circuit (contract {@code OrganizationWorkflow}). */
    record ClientWorkflow(UUID organizationId, Summary workflow, int publishedVersion, boolean chosen,
                          Settings settings, List<Node> nodes, long version) {
        /** From the use case's view. */
        static ClientWorkflow of(OrganizationWorkflow o) {
            return new ClientWorkflow(o.organizationId().value(), Summary.of(o.workflow()),
                    o.publishedVersion(), o.chosen(), Settings.of(o.settings()),
                    o.nodes().stream().map(Node::of).toList(), o.version());
        }
    }

    /** Body of {@code PUT /v1/admin/organizations/{id}/workflow} (contract {@code OrganizationWorkflowInput}). */
    record ClientWorkflowInput(@NotNull UUID workflowId, @NotNull @Valid Settings settings, Long version) {
    }

    /** {@code GET /v1/admin/workflows}: see {@link WorkflowAdministration#list}. */
    @GetMapping("/v1/admin/workflows")
    List<Summary> list(Perimeter perimeter) {
        return administration.list(perimeter).stream().map(Summary::of).toList();
    }

    /** {@code GET /v1/admin/workflows/{id}/versions/{n}}: see {@link WorkflowAdministration#version}. */
    @GetMapping("/v1/admin/workflows/{workflowId}/versions/{versionNumber}")
    Detail version(Perimeter perimeter, @PathVariable UUID workflowId, @PathVariable int versionNumber) {
        return Detail.of(administration.version(perimeter, workflowId, versionNumber));
    }

    /** {@code POST /v1/admin/workflows/{id}/versions/{n}/publish}: see {@link WorkflowAdministration#publish}. */
    @PostMapping("/v1/admin/workflows/{workflowId}/versions/{versionNumber}/publish")
    Detail publish(Perimeter perimeter, @PathVariable UUID workflowId, @PathVariable int versionNumber) {
        return Detail.of(administration.publish(perimeter, workflowId, versionNumber));
    }

    /** {@code GET /v1/admin/organizations/{id}/workflow}: see {@link WorkflowAdministration#organization}. */
    @GetMapping("/v1/admin/organizations/{organizationId}/workflow")
    ClientWorkflow organization(Perimeter perimeter, @PathVariable UUID organizationId) {
        return ClientWorkflow.of(administration.organization(perimeter, new OrganizationId(organizationId)));
    }

    /** {@code PUT /v1/admin/organizations/{id}/workflow}: see {@link WorkflowAdministration#choose}. */
    @PutMapping("/v1/admin/organizations/{organizationId}/workflow")
    ClientWorkflow choose(Perimeter perimeter, @PathVariable UUID organizationId,
                          @Valid @RequestBody ClientWorkflowInput body) {
        return ClientWorkflow.of(administration.choose(perimeter, new OrganizationId(organizationId),
                body.workflowId(), body.settings().toSettings(), Optional.ofNullable(body.version())));
    }
}
