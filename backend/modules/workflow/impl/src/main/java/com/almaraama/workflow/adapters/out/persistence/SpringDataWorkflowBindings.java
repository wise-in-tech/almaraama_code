package com.almaraama.workflow.adapters.out.persistence;

import com.almaraama.workflow.domain.WorkflowBinding;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code workflow.workflow_binding}; no delete (M2). */
interface SpringDataWorkflowBindings extends Repository<WorkflowBinding, UUID> {

    /** The organization's binding (no site) for a subject type. */
    @Query("select b from WorkflowBinding b where b.organizationId = :organization and b.siteId is null "
            + "and b.subjectType = :subject")
    Optional<WorkflowBinding> ofOrganization(@Param("organization") UUID organization,
                                             @Param("subject") String subject);

    /** Bindings whose options mention auto-close. */
    @Query("select b from WorkflowBinding b where b.settings like '%autoCloseAfterDays%'")
    List<WorkflowBinding> withAutoClose();

    /** Number of bindings to a circuit. */
    long countByWorkflowId(UUID workflowId);

    /** Inserts or updates a binding. */
    WorkflowBinding save(WorkflowBinding binding);
}
