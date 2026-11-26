package com.almaraama.workflow.domain;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * Which circuit applies to an organization, with its options (H5, ADR-036). The options are JSON text read at each
 * use, so they apply at once to open subjects too (criterion 19).
 */
@Entity
@Table(schema = "workflow", name = "workflow_binding")
public class WorkflowBinding {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "subject_type", nullable = false)
    private String subjectType;

    @Column(name = "workflow_id", nullable = false)
    private UUID workflowId;

    @Column(name = "settings", nullable = false)
    private String settings;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected WorkflowBinding() {
    }

    /** The first choice of an organization. */
    public static WorkflowBinding of(OrganizationId organization, String subjectType, UUID workflowId,
                                     String settingsJson, Instant now) {
        WorkflowBinding binding = new WorkflowBinding();
        binding.id = UuidV7.next();
        binding.organizationId = organization.value();
        binding.subjectType = subjectType;
        binding.createdAt = now;
        binding.change(workflowId, settingsJson, now);
        return binding;
    }

    /** Changes the circuit (new subjects only) and the options (at once). */
    public void change(UUID workflowId, String settingsJson, Instant now) {
        this.workflowId = workflowId;
        this.settings = settingsJson;
        this.updatedAt = now;
    }

    /** Identifier. */
    public UUID id() {
        return id;
    }

    /** The organization. */
    public OrganizationId organizationId() {
        return new OrganizationId(organizationId);
    }

    /** The chosen circuit. */
    public UUID workflowId() {
        return workflowId;
    }

    /** Options as JSON text. */
    public String settingsJson() {
        return settings;
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }
}
