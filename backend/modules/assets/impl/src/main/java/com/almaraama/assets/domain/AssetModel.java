package com.almaraama.assets.domain;

import com.almaraama.shared.access.ScopeType;
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
 * Model (version) of a family of equipment (G1). The platform's models are shared; an organization may add its own
 * models, also to a platform family, seen only by it. Archived, never deleted (M2).
 */
@Entity
@Table(schema = "assets", name = "asset_model")
public class AssetModel {

    @Id
    private UUID id;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_scope", nullable = false)
    private ScopeType ownerScope;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "model_version")
    private String modelVersion;

    @Column(name = "manufacturer")
    private String manufacturer;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected AssetModel() {
    }

    /** A new model of the family; {@code ASSET_MODEL_INVALID} for a missing or too long text. */
    public static AssetModel create(UUID familyId, Optional<OrganizationId> organization, String name,
                                    String modelVersion, String manufacturer, Instant now) {
        AssetModel model = new AssetModel();
        model.id = UuidV7.next();
        model.familyId = familyId;
        model.ownerScope = organization.isPresent() ? ScopeType.ORGANIZATION : ScopeType.PLATFORM;
        model.organizationId = organization.map(OrganizationId::value).orElse(null);
        model.createdAt = now;
        model.update(name, modelVersion, manufacturer, true, now);
        return model;
    }

    /** Renames or archives the model. {@code ASSET_MODEL_INVALID}. */
    public void update(String name, String modelVersion, String manufacturer, boolean active, Instant now) {
        this.name = Labels.required(name, 100, "ASSET_MODEL_INVALID");
        this.modelVersion = Labels.optional(modelVersion, 50, "ASSET_MODEL_INVALID");
        this.manufacturer = Labels.optional(manufacturer, 100, "ASSET_MODEL_INVALID");
        this.active = active;
        this.updatedAt = now;
    }

    /** Whether an organization may use the model: a platform model or its own. */
    public boolean usableBy(OrganizationId organization) {
        return organizationId == null || organizationId.equals(organization.value());
    }

    /** Identifier. */
    public UUID id() {
        return id;
    }

    /** The family. */
    public UUID familyId() {
        return familyId;
    }

    /** {@code PLATFORM} or {@code ORGANIZATION}. */
    public ScopeType ownerScope() {
        return ownerScope;
    }

    /** The owner of an organization's model. */
    public Optional<OrganizationId> organizationId() {
        return Optional.ofNullable(organizationId).map(OrganizationId::new);
    }

    /** Commercial name. */
    public String name() {
        return name;
    }

    /** Version or reference of the manufacturer. */
    public Optional<String> modelVersion() {
        return Optional.ofNullable(modelVersion);
    }

    /** Manufacturer. */
    public Optional<String> manufacturer() {
        return Optional.ofNullable(manufacturer);
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
