package com.almaraama.workflow.adapters.out.persistence;

import com.almaraama.workflow.domain.WorkflowNode;
import java.util.List;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Spring Data access to {@code workflow.workflow_node}; read only for the application. */
interface SpringDataWorkflowNodes extends Repository<WorkflowNode, UUID> {

    /** The steps of a version. */
    List<WorkflowNode> findByVersionId(UUID versionId);

    /** Steps whose configuration mentions {@code autoClose}. */
    @Query("select n from WorkflowNode n where n.config like '%autoClose%'")
    List<WorkflowNode> autoClose();
}
