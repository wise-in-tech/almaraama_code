package com.almaraama.workflow.adapters.out.persistence;

import com.almaraama.workflow.domain.Workflow;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code workflow.workflow}; no delete (M2). */
interface SpringDataWorkflows extends Repository<Workflow, UUID> {

    /** Loads a circuit. */
    Optional<Workflow> findById(UUID id);

    /** The circuits of a subject type. */
    List<Workflow> findBySubjectType(String subjectType);

    /** A platform circuit by code. */
    @Query("select w from Workflow w where w.ownerScope = 'PLATFORM' and w.code = :code")
    Optional<Workflow> platform(@Param("code") String code);
}
