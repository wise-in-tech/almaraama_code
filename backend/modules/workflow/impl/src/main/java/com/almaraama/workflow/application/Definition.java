package com.almaraama.workflow.application;

import com.almaraama.workflow.domain.WorkflowNode;
import com.almaraama.workflow.domain.WorkflowTransition;
import com.almaraama.workflow.domain.WorkflowVersion;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * A circuit version loaded once: its steps and passages. A published version never changes (ADR-036), so the engine
 * keeps it in memory.
 */
record Definition(WorkflowVersion version, List<WorkflowNode> nodes, List<WorkflowTransition> transitions,
                  Map<UUID, WorkflowNode> nodeById) {

    /** From the rows of a version. */
    static Definition of(WorkflowVersion version, List<WorkflowNode> nodes, List<WorkflowTransition> transitions) {
        return new Definition(version, List.copyOf(nodes), List.copyOf(transitions),
                nodes.stream().collect(Collectors.toMap(WorkflowNode::id, Function.identity())));
    }

    /** The start step: the step of category {@code OPEN} that no passage enters (ADR-036). */
    Optional<WorkflowNode> start() {
        return nodes.stream()
                .filter(n -> n.category() == com.almaraama.workflow.api.NodeCategory.OPEN)
                .filter(n -> transitions.stream().noneMatch(t -> t.toNodeId().equals(n.id())))
                .findFirst();
    }

    /** The passages leaving a step, by sort order. */
    List<WorkflowTransition> from(UUID nodeId) {
        return transitions.stream().filter(t -> t.fromNodeId().equals(nodeId)).toList();
    }

    /** A step by code. */
    Optional<WorkflowNode> node(String code) {
        return nodes.stream().filter(n -> n.code().equals(code)).findFirst();
    }
}
