package com.almaraama.maintenance.domain;

import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.i18n.LocalizedText;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** Category of problems (H2): security, cleaning, technical… Platform default (M7) or an organization's own. */
@Entity
@Table(schema = "maintenance", name = "issue_category")
public class IssueCategory {

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_scope", nullable = false)
    private ScopeType ownerScope;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "code", nullable = false)
    private String code;

    @Column(name = "name_ar")
    private String nameAr;

    @Column(name = "name_en")
    private String nameEn;

    @Column(name = "name_fr")
    private String nameFr;

    @Column(name = "sort_order", nullable = false)
    private int sortOrder;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected IssueCategory() {
    }

    /** A new category; {@code ISSUE_CATEGORY_CODE_INVALID}, {@code ISSUE_CATEGORY_NAME_INVALID}. */
    public static IssueCategory create(Optional<OrganizationId> organization, String code, LocalizedText name,
                                       int sortOrder, Instant now) {
        IssueCategory category = new IssueCategory();
        category.id = UuidV7.next();
        category.ownerScope = organization.isPresent() ? ScopeType.ORGANIZATION : ScopeType.PLATFORM;
        category.organizationId = organization.map(OrganizationId::value).orElse(null);
        category.code = Checks.code(code, 30, "ISSUE_CATEGORY_CODE_INVALID");
        category.createdAt = now;
        category.update(name, sortOrder, true, now);
        return category;
    }

    /** Renames, reorders, archives or reactivates. */
    public void update(LocalizedText name, int sortOrder, boolean active, Instant now) {
        LocalizedText checked = Checks.name(name, "ISSUE_CATEGORY_NAME_INVALID");
        this.nameAr = checked.ar();
        this.nameEn = checked.en();
        this.nameFr = checked.fr();
        this.sortOrder = sortOrder;
        this.active = active;
        this.updatedAt = now;
    }

    /** Whether the organization may use it: a platform default or its own. */
    public boolean usableBy(OrganizationId organization) {
        return organizationId == null || organizationId.equals(organization.value());
    }

    /** Identifier. */
    public UUID id() {
        return id;
    }

    /** {@code PLATFORM} or {@code ORGANIZATION}. */
    public ScopeType ownerScope() {
        return ownerScope;
    }

    /** Owner of an organization's category. */
    public Optional<OrganizationId> organizationId() {
        return Optional.ofNullable(organizationId).map(OrganizationId::new);
    }

    /** Readable code. */
    public String code() {
        return code;
    }

    /** Names with the fallback of M6. */
    public LocalizedText name() {
        return new LocalizedText(nameAr, nameEn, nameFr).filled(code);
    }

    /** Display order. */
    public int sortOrder() {
        return sortOrder;
    }

    /** Whether offered. */
    public boolean active() {
        return active;
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }
}
