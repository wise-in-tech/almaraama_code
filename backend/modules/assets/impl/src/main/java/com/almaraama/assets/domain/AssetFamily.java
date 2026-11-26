package com.almaraama.assets.domain;

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

/**
 * Family of equipment (G1): elevator, fire extinguisher, gate… A default of the platform (M7) or an organization's
 * own addition. Its code never changes; archived, never deleted (M2).
 */
@Entity
@Table(schema = "assets", name = "asset_family")
public class AssetFamily {

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
    protected AssetFamily() {
    }

    /**
     * A new family: of the platform when {@code organization} is empty, else of that organization. {@code
     * ASSET_FAMILY_CODE_INVALID}, {@code ASSET_FAMILY_NAME_INVALID}.
     */
    public static AssetFamily create(Optional<OrganizationId> organization, String code, LocalizedText name,
                                     int sortOrder, Instant now) {
        AssetFamily family = new AssetFamily();
        family.id = UuidV7.next();
        family.ownerScope = organization.isPresent() ? ScopeType.ORGANIZATION : ScopeType.PLATFORM;
        family.organizationId = organization.map(OrganizationId::value).orElse(null);
        family.code = Labels.code(code, "ASSET_FAMILY_CODE_INVALID");
        family.createdAt = now;
        family.update(name, sortOrder, true, now);
        return family;
    }

    /** Renames, reorders, archives or reactivates the family. {@code ASSET_FAMILY_NAME_INVALID}. */
    public void update(LocalizedText name, int sortOrder, boolean active, Instant now) {
        LocalizedText checked = Labels.name(name, "ASSET_FAMILY_NAME_INVALID");
        this.nameAr = checked.ar();
        this.nameEn = checked.en();
        this.nameFr = checked.fr();
        this.sortOrder = sortOrder;
        this.active = active;
        this.updatedAt = now;
    }

    /** Whether an organization may use the family: a platform default or its own. */
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

    /** The owner of an organization's family. */
    public Optional<OrganizationId> organizationId() {
        return Optional.ofNullable(organizationId).map(OrganizationId::new);
    }

    /** Readable code, used in the Excel import. */
    public String code() {
        return code;
    }

    /** Names, with the fallback of M6. */
    public LocalizedText name() {
        return new LocalizedText(nameAr, nameEn, nameFr).filled(code);
    }

    /** Display order. */
    public int sortOrder() {
        return sortOrder;
    }

    /** Whether new equipment may receive it. */
    public boolean active() {
        return active;
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }
}
