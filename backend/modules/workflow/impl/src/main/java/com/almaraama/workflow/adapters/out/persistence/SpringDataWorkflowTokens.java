package com.almaraama.workflow.adapters.out.persistence;

import com.almaraama.workflow.domain.WorkflowToken;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Limit;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code workflow.workflow_token}; no delete (M2). */
interface SpringDataWorkflowTokens extends Repository<WorkflowToken, UUID> {

    /** The active token of an instance. */
    Optional<WorkflowToken> findByInstanceIdAndActiveTrue(UUID instanceId);

    /** Active tokens of an organization in these steps, entered before a moment, oldest first. */
    @Query("select t from WorkflowToken t, WorkflowInstance i where t.instanceId = i.id and t.active = true "
            + "and i.organizationId = :organization and t.nodeId in :nodes and t.enteredAt <= :before "
            + "order by t.enteredAt")
    List<WorkflowToken> waiting(@Param("organization") UUID organization, @Param("nodes") Collection<UUID> nodes,
                                @Param("before") Instant before, Limit limit);

    /** Inserts or updates a token. */
    WorkflowToken save(WorkflowToken token);

    /** Updates a token and flushes at once. */
    WorkflowToken saveAndFlush(WorkflowToken token);
}
