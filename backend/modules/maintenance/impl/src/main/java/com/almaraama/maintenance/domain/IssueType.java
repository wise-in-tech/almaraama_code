package com.almaraama.maintenance.domain;

import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.i18n.LocalizedText;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/**
 * Precise type of problem (H2): whether an equipment is required (H3), its default visibility (H8), the equipment
 * families it fits (QR code filter, criterion 8). Platform default (M7) or an organization's own.
 */
@Entity
@Table(schema = "maintenance", name = "issue_type")
public class IssueType {

    @Id
    private UUID id;

    @Column(name = "category_id", nullable = false)
    private UUID categoryId;

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

    @Column(name = "asset_required", nullable = false)
    private boolean assetRequired;

    @Enumerated(EnumType.STRING)
    @Column(name = "default_visibility", nullable = false)
    private Visibility defaultVisibility;

    @Convert(converter = UuidListConverter.class)
    @Column(name = "asset_family_ids", nullable = false)
    private List<UUID> assetFamilyIds;

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

    /** What may change on a type. */
    public record Details(LocalizedText name, boolean assetRequired, Visibility defaultVisibility,
                          List<UUID> assetFamilyIds, int sortOrder) {
    }

    /** For JPA only. */
    protected IssueType() {
    }

    /** A new type in a category; {@code ISSUE_TYPE_CODE_INVALID}, {@code ISSUE_TYPE_NAME_INVALID}. */
    public static IssueType create(UUID categoryId, Optional<OrganizationId> organization, String code,
                                   Details details, Instant now) {
        IssueType type = new IssueType();
        type.id = UuidV7.next();
        type.categoryId = categoryId;
        type.ownerScope = organization.isPresent() ? ScopeType.ORGANIZATION : ScopeType.PLATFORM;
        type.organizationId = organization.map(OrganizationId::value).orElse(null);
        type.code = Checks.code(code, 40, "ISSUE_TYPE_CODE_INVALID");
        type.createdAt = now;
        type.update(details, true, now);
        return type;
    }

    /** Changes what may change; code and category never change. */
    public void update(Details details, boolean active, Instant now) {
        LocalizedText checked = Checks.name(details.name(), "ISSUE_TYPE_NAME_INVALID");
        this.nameAr = checked.ar();
        this.nameEn = checked.en();
        this.nameFr = checked.fr();
        this.assetRequired = details.assetRequired();
        this.defaultVisibility = details.defaultVisibility();
        this.assetFamilyIds = List.copyOf(details.assetFamilyIds());
        this.sortOrder = details.sortOrder();
        this.active = active;
        this.updatedAt = now;
    }

    /** Whether the organization may use it: a platform default or its own. */
    public boolean usableBy(OrganizationId organization) {
        return organizationId == null || organizationId.equals(organization.value());
    }

    /** Whether it fits an equipment of this family (no family listed: every family). */
    public boolean fits(UUID familyId) {
        return assetFamilyIds.isEmpty() || assetFamilyIds.contains(familyId);
    }

    /** Identifier. */
    public UUID id() {
        return id;
    }

    /** Category. */
    public UUID categoryId() {
        return categoryId;
    }

    /** {@code PLATFORM} or {@code ORGANIZATION}. */
    public ScopeType ownerScope() {
        return ownerScope;
    }

    /** Owner of an organization's type. */
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

    /** H3: an equipment is mandatory. */
    public boolean assetRequired() {
        return assetRequired;
    }

    /** H8: visibility of its tickets unless the site says otherwise. */
    public Visibility defaultVisibility() {
        return defaultVisibility;
    }

    /** Families it fits; empty means all. */
    public List<UUID> assetFamilyIds() {
        return assetFamilyIds;
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
