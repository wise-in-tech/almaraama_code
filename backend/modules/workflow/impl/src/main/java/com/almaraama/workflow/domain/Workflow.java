package com.almaraama.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.util.Optional;
import java.util.UUID;

/**
 * A circuit (ADR-036): owned by the platform, an organization or a site; its versions hold the steps and passages.
 * Loaded by the architect in the tables in P1 (no creation by the API).
 */
@Entity
@Table(schema = "workflow", name = "workflow")
public class Workflow {

    /** Code of the platform's ticket circuit, used when an organization chose none. */
    public static final String DEFAULT_TICKET = "default-ticket";

    @Id
    private UUID id;

    @Column(name = "owner_scope", nullable = false)
    private String ownerScope;

    @Column(name = "owner_organization_id")
    private UUID ownerOrganizationId;

    @Column(name = "subject_type", nullable = false)
    private String subjectType;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name", nullable = false)
    private String name;

    @Version
    private Long version;

    /** For JPA only. */
    protected Workflow() {
    }

    /** Identifier. */
    public UUID id() {
        return id;
    }

    /** {@code PLATFORM}, {@code ORGANIZATION} or {@code SITE}. */
    public String ownerScope() {
        return ownerScope;
    }

    /** The owner organization of an organization's circuit. */
    public Optional<UUID> ownerOrganizationId() {
        return Optional.ofNullable(ownerOrganizationId);
    }

    /** What it applies to ({@code TICKET} in P1). */
    public String subjectType() {
        return subjectType;
    }

    /** Readable code. */
    public String code() {
        return code;
    }

    /** Name shown in the back-office. */
    public String name() {
        return name;
    }
}
