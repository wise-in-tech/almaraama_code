package com.almaraama.workflow.adapters.out.persistence;

import com.almaraama.workflow.domain.WorkflowEvent;
import java.util.List;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data access to {@code workflow.workflow_event}: insert and read only (H9). */
interface SpringDataWorkflowEvents extends Repository<WorkflowEvent, UUID> {

    /** The history of an instance, oldest first. */
    List<WorkflowEvent> findByInstanceIdOrderByOccurredAtAsc(UUID instanceId);

    /** Appends an entry. */
    WorkflowEvent save(WorkflowEvent event);
}
