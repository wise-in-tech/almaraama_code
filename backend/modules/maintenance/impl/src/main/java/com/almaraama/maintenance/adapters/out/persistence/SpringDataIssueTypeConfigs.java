package com.almaraama.maintenance.adapters.out.persistence;

import com.almaraama.maintenance.domain.IssueTypeConfig;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Spring Data access to {@code maintenance.issue_type_config}; no delete (M2). */
interface SpringDataIssueTypeConfigs extends Repository<IssueTypeConfig, UUID> {

    /** The setting of a type on a site. */
    Optional<IssueTypeConfig> findBySiteIdAndIssueTypeId(UUID siteId, UUID issueTypeId);

    /** The settings of a site. */
    List<IssueTypeConfig> findBySiteId(UUID siteId);

    /** Inserts or updates a setting. */
    IssueTypeConfig save(IssueTypeConfig config);
}
