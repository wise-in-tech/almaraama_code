package com.almaraama.maintenance.adapters.out.persistence;

import com.almaraama.maintenance.domain.IssueCategory;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code maintenance.issue_category}; no delete (M2). */
interface SpringDataIssueCategories extends Repository<IssueCategory, UUID> {

    /** Loads a category. */
    Optional<IssueCategory> findById(UUID id);

    /** The platform's categories and the organization's. */
    @Query("select c from IssueCategory c where (c.organizationId is null or c.organizationId = :organization) "
            + "and (:archived = true or c.active = true) order by c.sortOrder, c.code")
    List<IssueCategory> usable(@Param("organization") UUID organization, @Param("archived") boolean archived);

    /** The platform's categories. */
    @Query("select c from IssueCategory c where c.organizationId is null "
            + "and (:archived = true or c.active = true) order by c.sortOrder, c.code")
    List<IssueCategory> platform(@Param("archived") boolean archived);

    /** Inserts or updates a category. */
    IssueCategory save(IssueCategory category);
}
