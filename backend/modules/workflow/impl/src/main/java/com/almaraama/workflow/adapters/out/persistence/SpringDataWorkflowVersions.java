package com.almaraama.workflow.adapters.out.persistence;

import com.almaraama.workflow.domain.WorkflowVersion;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data access to {@code workflow.workflow_version}; no delete (M2). */
interface SpringDataWorkflowVersions extends Repository<WorkflowVersion, UUID> {

    /** Loads a version. */
    Optional<WorkflowVersion> findById(UUID id);

    /** The versions of a circuit by number. */
    List<WorkflowVersion> findByWorkflowIdOrderByNumberAsc(UUID workflowId);

    /** Updates a version. */
    WorkflowVersion save(WorkflowVersion version);
}
