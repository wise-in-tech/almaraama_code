package com.almaraama.assets.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Optional;
import java.util.UUID;
import java.util.regex.Pattern;

/**
 * An equipment of a site (G1 to G4): its family and model, where it is (G2), its state, its supplier, its
 * characteristics and its QR code. Documents and warranties are rows of their own, loaded with it. The site and the
 * family never change; archived, never deleted (M2).
 */
@Entity
@Table(schema = "assets", name = "asset")
public class Asset {

    private static final Pattern REFERENCE = Pattern.compile("^[A-Za-z0-9][A-Za-z0-9_./-]{0,39}$");

    /** Where an equipment is: a building (and floor), an outdoor area, or the site as a whole (G2). */
    public record Location(Optional<BuildingId> buildingId, Optional<Integer> floor, Optional<UUID> outdoorAreaId) {

        /** A building or an outdoor area, never both; a floor only inside a building. {@code ASSET_LOCATION_INVALID}. */
        public Location {
            if ((buildingId.isPresent() && outdoorAreaId.isPresent()) || (floor.isPresent() && buildingId.isEmpty())) {
                throw new BusinessException("ASSET_LOCATION_INVALID", ErrorCategory.RULE_VIOLATION,
                        "A building or an outdoor area; a floor only in a building");
            }
        }
    }

    /** What may change on an equipment. */
    public record Details(Optional<UUID> modelId, String name, String reference, Location location, AssetState state,
                          String serialNumber, Optional<LocalDate> commissionedOn, Optional<UUID> supplierId,
                          String attributesJson) {
    }

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "model_id")
    private UUID modelId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "reference", nullable = false)
    private String reference;

    @Column(name = "building_id")
    private UUID buildingId;

    @Column(name = "floor")
    private Integer floor;

    @Column(name = "outdoor_area_id")
    private UUID outdoorAreaId;

    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false)
    private AssetState state;

    @Column(name = "serial_number")
    private String serialNumber;

    @Column(name = "commissioned_on")
    private LocalDate commissionedOn;

    @Column(name = "supplier_id")
    private UUID supplierId;

    @Column(name = "attributes", nullable = false)
    private String attributes;

    @Column(name = "qr_code", nullable = false)
    private String qrCode;

    @Column(name = "archived_at")
    private Instant archivedAt;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected Asset() {
    }

    /**
     * A new equipment with a fresh QR code (G4). The use case has checked the family, model, location and supplier
     * against the site; {@code ASSET_REFERENCE_INVALID}, {@code ASSET_NAME_INVALID}, {@code ASSET_LOCATION_INVALID}.
     */
    public static Asset create(OrganizationId organization, SiteId site, UUID familyId, Details details, Instant now) {
        Asset asset = new Asset();
        asset.id = UuidV7.next();
        asset.organizationId = organization.value();
        asset.siteId = site.value();
        asset.familyId = familyId;
        asset.qrCode = QrCodes.next();
        asset.createdAt = now;
        asset.update(details, now);
        return asset;
    }

    /** Changes the equipment. {@code ASSET_ARCHIVED} once archived; same checks as {@link #create}. */
    public void update(Details details, Instant now) {
        requireActive();
        if (details.reference() == null || !REFERENCE.matcher(details.reference()).matches()) {
            throw new BusinessException("ASSET_REFERENCE_INVALID", ErrorCategory.VALIDATION, "Reference");
        }
        this.modelId = details.modelId().orElse(null);
        this.name = Labels.required(details.name(), 120, "ASSET_NAME_INVALID");
        this.reference = details.reference();
        this.buildingId = details.location().buildingId().map(BuildingId::value).orElse(null);
        this.floor = details.location().floor().orElse(null);
        this.outdoorAreaId = details.location().outdoorAreaId().orElse(null);
        this.state = details.state() == null ? AssetState.IN_SERVICE : details.state();
        this.serialNumber = Labels.optional(details.serialNumber(), 80, "ASSET_SERIAL_INVALID");
        this.commissionedOn = details.commissionedOn().orElse(null);
        this.supplierId = details.supplierId().orElse(null);
        this.attributes = details.attributesJson() == null ? "{}" : details.attributesJson();
        this.updatedAt = now;
    }

    /** Gives the equipment a new QR code; the old one stops answering (G4). {@code ASSET_ARCHIVED}. */
    public void rotateQrCode(Instant now) {
        requireActive();
        this.qrCode = QrCodes.next();
        this.updatedAt = now;
    }

    /** Archives the equipment (M2). {@code ASSET_ARCHIVED} when already archived. */
    public void archive(Instant now) {
        requireActive();
        this.archivedAt = now;
        this.updatedAt = now;
    }

    /** Marks a change of a document or a warranty, so the version moves. */
    public void touch(Instant now) {
        requireActive();
        this.updatedAt = now;
    }

    /** {@code ASSET_ARCHIVED} once archived. */
    private void requireActive() {
        if (archivedAt != null) {
            throw new BusinessException("ASSET_ARCHIVED", ErrorCategory.RULE_VIOLATION, "Equipment archived");
        }
    }

    /** Identifier. */
    public UUID id() {
        return id;
    }

    /** Owner organization. */
    public OrganizationId organizationId() {
        return new OrganizationId(organizationId);
    }

    /** Site. */
    public SiteId siteId() {
        return new SiteId(siteId);
    }

    /** Family (G1). */
    public UUID familyId() {
        return familyId;
    }

    /** Model, if known. */
    public Optional<UUID> modelId() {
        return Optional.ofNullable(modelId);
    }

    /** Name shown to everyone. */
    public String name() {
        return name;
    }

    /** Reference, unique in the site. */
    public String reference() {
        return reference;
    }

    /** Where it is (G2). */
    public Location location() {
        return new Location(Optional.ofNullable(buildingId).map(BuildingId::new), Optional.ofNullable(floor),
                Optional.ofNullable(outdoorAreaId));
    }

    /** State. */
    public AssetState state() {
        return state;
    }

    /** Serial number. */
    public Optional<String> serialNumber() {
        return Optional.ofNullable(serialNumber);
    }

    /** Date of commissioning. */
    public Optional<LocalDate> commissionedOn() {
        return Optional.ofNullable(commissionedOn);
    }

    /** Supplier contact (G3). */
    public Optional<UUID> supplierId() {
        return Optional.ofNullable(supplierId);
    }

    /** Characteristics as JSON text keyed by definition code, already validated (G3, ADR-039). */
    public String attributesJson() {
        return attributes;
    }

    /** Current QR code (G4). */
    public String qrCode() {
        return qrCode;
    }

    /** Whether archived (M2). */
    public boolean archived() {
        return archivedAt != null;
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }
}
