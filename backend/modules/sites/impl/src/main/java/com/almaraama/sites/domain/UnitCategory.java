package com.almaraama.sites.domain;

import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UuidV7;
import com.almaraama.sites.api.UnitType;
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
import java.util.regex.Pattern;

/**
 * Category of a unit (A6): the typology of an apartment (studio, T1 to T5, duplex), a free category for the other
 * types ("grand magasin", "parking couvert"). It will drive the service charge (F1, P2). Defaults kept by the platform
 * administrators ({@code PLATFORM}, M7), completed by a promoteur for its organization or by a manager for one site.
 * Never deleted: archived (M2).
 */
@Entity
@Table(schema = "sites", name = "unit_category")
public class UnitCategory {

    private static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{0,29}$");

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false)
    private ScopeType scopeType;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "site_id")
    private UUID siteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "unit_type", nullable = false)
    private UnitType unitType;

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
    protected UnitCategory() {
    }

    /**
     * A category added by a promoteur ({@code site} empty) or a manager ({@code site} given): {@code
     * UNIT_CATEGORY_CODE_INVALID}, {@code UNIT_CATEGORY_NAME_INVALID}.
     */
    public static UnitCategory create(OrganizationId organization, Optional<SiteId> site, UnitType unitType,
                                      String code, LocalizedName name, int sortOrder, Instant now) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new BusinessException("UNIT_CATEGORY_CODE_INVALID", ErrorCategory.VALIDATION, "Readable code");
        }
        UnitCategory category = new UnitCategory();
        category.id = UuidV7.next();
        category.scopeType = site.isPresent() ? ScopeType.SITE : ScopeType.ORGANIZATION;
        category.organizationId = organization.value();
        category.siteId = site.map(SiteId::value).orElse(null);
        category.unitType = unitType;
        category.code = code;
        category.active = true;
        category.createdAt = now;
        category.describe(name, sortOrder, now);
        return category;
    }

    /**
     * A6, M7: a default category of the platform, offered to every organization; added by our team. Same errors as
     * {@link #create}.
     */
    public static UnitCategory platformDefault(UnitType unitType, String code, LocalizedName name, int sortOrder,
                                               Instant now) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new BusinessException("UNIT_CATEGORY_CODE_INVALID", ErrorCategory.VALIDATION, "Readable code");
        }
        UnitCategory category = new UnitCategory();
        category.id = UuidV7.next();
        category.scopeType = ScopeType.PLATFORM;
        category.unitType = unitType;
        category.code = code;
        category.active = true;
        category.createdAt = now;
        category.describe(name, sortOrder, now);
        return category;
    }

    /** Renames, reorders, archives or reactivates the category; type and code never change. */
    public void update(LocalizedName name, int sortOrder, boolean active, Instant now) {
        describe(name, sortOrder, now);
        this.active = active;
    }

    /** Sets the names (at least one language, 100 characters each) and the order. */
    private void describe(LocalizedName name, int sortOrder, Instant now) {
        this.nameAr = Texts.optional(name.ar(), 100, "UNIT_CATEGORY_NAME_INVALID");
        this.nameEn = Texts.optional(name.en(), 100, "UNIT_CATEGORY_NAME_INVALID");
        this.nameFr = Texts.optional(name.fr(), 100, "UNIT_CATEGORY_NAME_INVALID");
        if (nameAr == null && nameEn == null && nameFr == null) {
            throw new BusinessException("UNIT_CATEGORY_NAME_INVALID", ErrorCategory.VALIDATION, "A name is needed");
        }
        this.sortOrder = sortOrder;
        this.updatedAt = now;
    }

    /**
     * Whether a unit of this site may use the category: a platform default, the site's organization's own, or the
     * site's own.
     */
    public boolean usableOn(OrganizationId organization, SiteId site) {
        return switch (scopeType) {
            case PLATFORM -> true;
            case ORGANIZATION -> organization.value().equals(organizationId);
            case SITE -> site.value().equals(siteId);
        };
    }

    /** Identifier of the category. */
    public UUID id() {
        return id;
    }

    /** Platform default, organization's or site's category. */
    public ScopeType scopeType() {
        return scopeType;
    }

    /** The organization of an organization or site category. */
    public Optional<OrganizationId> organizationId() {
        return Optional.ofNullable(organizationId).map(OrganizationId::new);
    }

    /** The site of a site category. */
    public Optional<SiteId> siteId() {
        return Optional.ofNullable(siteId).map(SiteId::new);
    }

    /** The unit type it classifies. */
    public UnitType unitType() {
        return unitType;
    }

    /** Readable code, unique per type in its scope. */
    public String code() {
        return code;
    }

    /**
     * Names with the fallback of M6 for missing languages: the language asked, else French, English, Arabic, else the
     * code.
     */
    public LocalizedName name() {
        String any = nameFr != null ? nameFr : nameEn != null ? nameEn : nameAr != null ? nameAr : code;
        return new LocalizedName(nameAr != null ? nameAr : any, nameEn != null ? nameEn : any,
                nameFr != null ? nameFr : any);
    }

    /** Display order. */
    public int sortOrder() {
        return sortOrder;
    }

    /** Whether new units may receive it; archived categories stay on their units (M2). */
    public boolean active() {
        return active;
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }
}
