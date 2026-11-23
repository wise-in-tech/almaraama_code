package com.almaraama.sites.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/** A site of an organization (A1): a residence in P1. Its address lives in the address table. Never deleted (M2). */
@Entity
@Table(schema = "sites", name = "site")
public class Site {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private SiteType type;

    @Column(name = "name", nullable = false)
    private String name;

    @Enumerated(EnumType.STRING)
    @Column(name = "lifecycle_status", nullable = false)
    private SiteLifecycleStatus lifecycleStatus;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected Site() {
    }

    /** A5: P1 manages residential sites only. */
    public static Site create(OrganizationId organizationId, SiteType type, String name,
                              SiteLifecycleStatus lifecycleStatus, Instant now) {
        if (type != SiteType.RESIDENTIAL) {
            throw new BusinessException("SITE_TYPE_NOT_AVAILABLE", ErrorCategory.RULE_VIOLATION,
                    "Only residential sites in P1 (A5)");
        }
        Site site = new Site();
        site.id = SiteId.newId().value();
        site.organizationId = organizationId.value();
        site.type = type;
        site.active = true;
        site.createdAt = now;
        site.update(name, lifecycleStatus, true, now);
        return site;
    }

    /** Changes name, lifecycle status and state; {@code SITE_NAME_INVALID}. */
    public void update(String name, SiteLifecycleStatus lifecycleStatus, boolean active, Instant now) {
        this.name = Texts.required(name, 200, "SITE_NAME_INVALID");
        this.lifecycleStatus = lifecycleStatus;
        this.active = active;
        this.updatedAt = now;
    }

    /** Identifier of the site. */
    public SiteId id() {
        return new SiteId(id);
    }

    /** The organization owning the site. */
    public OrganizationId organizationId() {
        return new OrganizationId(organizationId);
    }

    /** Kind of site (A5). */
    public SiteType type() {
        return type;
    }

    /** Name of the site. */
    public String name() {
        return name;
    }

    /** Under construction or delivered. */
    public SiteLifecycleStatus lifecycleStatus() {
        return lifecycleStatus;
    }

    /** Whether the site is in use; archived otherwise (M2). */
    public boolean active() {
        return active;
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }
}
