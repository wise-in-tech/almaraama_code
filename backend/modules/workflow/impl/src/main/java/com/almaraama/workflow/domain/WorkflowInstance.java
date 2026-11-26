package com.almaraama.workflow.domain;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * The run of one subject (a ticket) through a circuit version (ADR-036). Its version moves at each passage, so two
 * passages at the same moment cannot both win.
 */
@Entity
@Table(schema = "workflow", name = "workflow_instance")
public class WorkflowInstance {

    @Id
    private UUID id;

    @Column(name = "version_id", nullable = false)
    private UUID versionId;

    @Column(name = "subject_type", nullable = false)
    private String subjectType;

    @Column(name = "subject_id", nullable = false)
    private UUID subjectId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "started_at", nullable = false)
    private Instant startedAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected WorkflowInstance() {
    }

    /** A new run on a published version. */
    public static WorkflowInstance start(UUID versionId, String subjectType, UUID subjectId,
                                         OrganizationId organization, SiteId site, Instant now) {
        WorkflowInstance instance = new WorkflowInstance();
        instance.id = UuidV7.next();
        instance.versionId = versionId;
        instance.subjectType = subjectType;
        instance.subjectId = subjectId;
        instance.organizationId = organization.value();
        instance.siteId = site.value();
        instance.startedAt = now;
        return instance;
    }

    /** Marks the end of the run (a {@code CLOSED} or {@code CANCELLED} step). */
    public void end(Instant now) {
        if (endedAt == null) {
            endedAt = now;
        }
    }

    /** Identifier. */
    public UUID id() {
        return id;
    }

    /** The version it runs on. */
    public UUID versionId() {
        return versionId;
    }

    /** Subject type. */
    public String subjectType() {
        return subjectType;
    }

    /** Subject identifier. */
    public UUID subjectId() {
        return subjectId;
    }

    /** The organization (options of the client, H5). */
    public OrganizationId organizationId() {
        return new OrganizationId(organizationId);
    }
}
