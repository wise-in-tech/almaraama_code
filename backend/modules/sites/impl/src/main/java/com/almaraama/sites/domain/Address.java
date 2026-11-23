package com.almaraama.sites.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
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
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;

/**
 * An address (A9): a zone of the attachment level of the organization's country (a commune for Algeria), the street
 * address, an optional postal code, label and map position. One table for every owner: a site has exactly one current
 * address; a building has its own, a copy of the site's made at its creation (decision of 20 November 2026), changed
 * independently afterwards. A changed address is replaced, the old row stays with {@code replacedAt} (history, M2).
 */
@Entity
@Table(schema = "sites", name = "address")
public class Address {

    /** What a person enters for an address. */
    public record Spec(String label, UUID zoneId, String addressLine, String postalCode, Double latitude,
                       Double longitude) {

        /**
         * Trims and checks the free text: {@code ADDRESS_LABEL_INVALID}, {@code ADDRESS_LINE_INVALID}, {@code
         * ADDRESS_POSTAL_CODE_INVALID}, {@code ADDRESS_POSITION_INVALID} (latitude and longitude together, in range).
         */
        public Spec {
            Objects.requireNonNull(zoneId, "zoneId");
            label = Texts.optional(label, 100, "ADDRESS_LABEL_INVALID");
            addressLine = Texts.required(addressLine, 300, "ADDRESS_LINE_INVALID");
            postalCode = Texts.optional(postalCode, 20, "ADDRESS_POSTAL_CODE_INVALID");
            if ((latitude == null) != (longitude == null)
                    || latitude != null && (Math.abs(latitude) > 90 || Math.abs(longitude) > 180)) {
                throw new BusinessException("ADDRESS_POSITION_INVALID", ErrorCategory.VALIDATION,
                        "Latitude and longitude together, within range");
            }
        }
    }

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "owner_type", nullable = false)
    private AddressOwnerType ownerType;

    @Column(name = "owner_id", nullable = false)
    private UUID ownerId;

    @Column(name = "label")
    private String label;

    @Column(name = "zone_id", nullable = false)
    private UUID zoneId;

    @Column(name = "address_line", nullable = false)
    private String addressLine;

    @Column(name = "postal_code")
    private String postalCode;

    @Column(name = "latitude")
    private Double latitude;

    @Column(name = "longitude")
    private Double longitude;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false)
    private AddressOrigin origin;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "replaced_at")
    private Instant replacedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected Address() {
    }

    /** A new current address typed for a site or building; the caller already checked the zone. */
    public static Address of(OrganizationId organizationId, SiteId siteId, AddressOwnerType ownerType, UUID ownerId,
                             Spec spec, Instant now) {
        Address address = new Address();
        address.id = UuidV7.next();
        address.organizationId = organizationId.value();
        address.siteId = siteId.value();
        address.ownerType = ownerType;
        address.ownerId = ownerId;
        address.label = spec.label();
        address.zoneId = spec.zoneId();
        address.addressLine = spec.addressLine();
        address.postalCode = spec.postalCode();
        address.latitude = spec.latitude();
        address.longitude = spec.longitude();
        address.origin = AddressOrigin.OWN;
        address.createdAt = now;
        return address;
    }

    /**
     * A copy of a site's current address for a new building (A9): same values, origin {@code SITE_COPY}. The copy
     * never follows later changes of the site's address.
     */
    public static Address copyForBuilding(Address siteAddress, UUID buildingId, Instant now) {
        Address copy = of(new OrganizationId(siteAddress.organizationId), new SiteId(siteAddress.siteId),
                AddressOwnerType.BUILDING, buildingId, siteAddress.toSpec(), now);
        copy.origin = AddressOrigin.SITE_COPY;
        return copy;
    }

    /**
     * A8, ADR-035: the same address in another zone, for the merge of a provisional zone into a validated one; keeps
     * its owner and origin. The caller replaces the current one.
     */
    public static Address movedToZone(Address current, UUID zoneId, Instant now) {
        Spec spec = current.toSpec();
        Address moved = of(new OrganizationId(current.organizationId), new SiteId(current.siteId), current.ownerType,
                current.ownerId, new Spec(spec.label(), zoneId, spec.addressLine(), spec.postalCode(),
                        spec.latitude(), spec.longitude()), now);
        moved.origin = current.origin;
        return moved;
    }

    /** The owner has a new address: this one becomes history. */
    public void replace(Instant now) {
        this.replacedAt = now;
    }

    /** Whether the specification describes exactly this address (no replacement needed). */
    public boolean sameAs(Spec spec) {
        return Objects.equals(label, spec.label()) && zoneId.equals(spec.zoneId())
                && addressLine.equals(spec.addressLine()) && Objects.equals(postalCode, spec.postalCode())
                && Objects.equals(latitude, spec.latitude()) && Objects.equals(longitude, spec.longitude());
    }

    /** The values of this address, to copy or compare them. */
    public Spec toSpec() {
        return new Spec(label, zoneId, addressLine, postalCode, latitude, longitude);
    }

    /** The site or building this address belongs to. */
    public UUID ownerId() {
        return ownerId;
    }

    /** Optional name, as people say it. */
    public Optional<String> label() {
        return Optional.ofNullable(label);
    }

    /** The zone of the attachment level (a commune for Algeria). */
    public UUID zoneId() {
        return zoneId;
    }

    /** The street address. */
    public String addressLine() {
        return addressLine;
    }

    /** Optional postal code (A9). */
    public Optional<String> postalCode() {
        return Optional.ofNullable(postalCode);
    }

    /** Optional map position, with the longitude. */
    public Optional<Double> latitude() {
        return Optional.ofNullable(latitude);
    }

    /** Optional map position, with the latitude. */
    public Optional<Double> longitude() {
        return Optional.ofNullable(longitude);
    }

    /** Typed for its owner, or a building's unchanged copy of its site's address. */
    public AddressOrigin origin() {
        return origin;
    }
}
