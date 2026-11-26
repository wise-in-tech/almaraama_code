package com.almaraama.workflow.adapters.out.persistence;

import com.almaraama.workflow.domain.WorkflowInstance;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data access to {@code workflow.workflow_instance}; no delete (M2). */
interface SpringDataWorkflowInstances extends Repository<WorkflowInstance, UUID> {

    /** Loads an instance. */
    Optional<WorkflowInstance> findById(UUID id);

    /** Inserts or updates an instance. */
    WorkflowInstance save(WorkflowInstance instance);
}
