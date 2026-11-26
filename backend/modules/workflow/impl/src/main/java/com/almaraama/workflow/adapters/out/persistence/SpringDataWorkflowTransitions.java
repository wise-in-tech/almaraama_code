package com.almaraama.workflow.adapters.out.persistence;

import com.almaraama.workflow.domain.WorkflowTransition;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data access to {@code workflow.workflow_transition}; read only for the application. */
interface SpringDataWorkflowTransitions extends Repository<WorkflowTransition, UUID> {

    /** The passages of a version by sort order. */
    List<WorkflowTransition> findByVersionIdOrderBySortOrderAscCodeAsc(UUID versionId);
}
