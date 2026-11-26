package com.almaraama.maintenance.adapters.out.persistence;

import com.almaraama.maintenance.domain.IssueType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Spring Data access to {@code maintenance.issue_type}; no delete (M2). */
interface SpringDataIssueTypes extends Repository<IssueType, UUID> {

    /** Loads a type. */
    Optional<IssueType> findById(UUID id);

    /** Loads several types. */
    List<IssueType> findByIdIn(Collection<UUID> ids);

    /** The platform's types of these categories and the organization's. */
    @Query("select t from IssueType t where t.categoryId in :categories "
            + "and (t.organizationId is null or t.organizationId = :organization) "
            + "and (:archived = true or t.active = true) order by t.sortOrder, t.code")
    List<IssueType> usable(@Param("categories") Collection<UUID> categories, @Param("organization") UUID organization,
                           @Param("archived") boolean archived);

    /** The platform's types of these categories. */
    @Query("select t from IssueType t where t.categoryId in :categories and t.organizationId is null "
            + "and (:archived = true or t.active = true) order by t.sortOrder, t.code")
    List<IssueType> platform(@Param("categories") Collection<UUID> categories, @Param("archived") boolean archived);

    /** Whether a type of the platform or of the organization has the code. */
    @Query("select count(t) > 0 from IssueType t where t.code = :code "
            + "and (t.organizationId is null or t.organizationId = :organization)")
    boolean codeTaken(@Param("organization") UUID organization, @Param("code") String code);

    /** Whether a platform type has the code. */
    @Query("select count(t) > 0 from IssueType t where t.code = :code and t.organizationId is null")
    boolean platformCodeTaken(@Param("code") String code);

    /** Inserts or updates a type. */
    IssueType save(IssueType type);
}
