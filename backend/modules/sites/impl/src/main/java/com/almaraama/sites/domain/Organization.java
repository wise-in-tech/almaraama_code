package com.almaraama.sites.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.sites.api.OrganizationType;
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
 * A client organization (A1): a promoteur, or a residence that is a client on its own. It carries the country, which
 * drives the currency, hosting and addresses of its sites (M4, M5). Never deleted (M2).
 */
@Entity
@Table(schema = "sites", name = "organization")
public class Organization {

    static final int MAX_NAME = 200;

    @Id
    private UUID id;

    @Enumerated(EnumType.STRING)
    @Column(name = "type", nullable = false)
    private OrganizationType type;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "country_code", nullable = false)
    private String countryCode;

    @Column(name = "contact_phone")
    private String contactPhone;

    @Column(name = "contact_email")
    private String contactEmail;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "deactivated_at")
    private Instant deactivatedAt;

    @Column(name = "deactivation_reason")
    private String deactivationReason;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected Organization() {
    }

    /** A new active client organization in a country of the reference data (the caller checked it). */
    public static Organization create(OrganizationType type, String name, String countryCode, String contactPhone,
                                      String contactEmail, Instant now) {
        Organization organization = new Organization();
        organization.id = OrganizationId.newId().value();
        organization.type = type;
        organization.countryCode = countryCode;
        organization.active = true;
        organization.createdAt = now;
        organization.describe(name, contactPhone, contactEmail, now);
        return organization;
    }

    /** Changes name and contact details. */
    public void update(String name, String contactPhone, String contactEmail, Instant now) {
        describe(name, contactPhone, contactEmail, now);
    }

    /**
     * C12, ADR-035: deactivated by our team; its people lose access at their next request, nothing is deleted (M2).
     * {@code ORGANIZATION_NOT_ACTIVE} when already deactivated.
     */
    public void deactivate(String reason, Instant now) {
        if (!active) {
            throw new BusinessException("ORGANIZATION_NOT_ACTIVE", ErrorCategory.RULE_VIOLATION, "Already deactivated");
        }
        active = false;
        deactivatedAt = now;
        deactivationReason = Texts.optional(reason, 500, "REASON_INVALID");
        updatedAt = now;
    }

    /** C12: active again with all its data; {@code ORGANIZATION_ALREADY_ACTIVE}. */
    public void reactivate(Instant now) {
        if (active) {
            throw new BusinessException("ORGANIZATION_ALREADY_ACTIVE", ErrorCategory.RULE_VIOLATION, "Active");
        }
        active = true;
        deactivatedAt = null;
        deactivationReason = null;
        updatedAt = now;
    }

    /** When the organization was onboarded. */
    public Instant createdAt() {
        return createdAt;
    }

    /** When it was deactivated, while it is. */
    public Optional<Instant> deactivatedAt() {
        return Optional.ofNullable(deactivatedAt);
    }

    /** Checks and sets name and contact: {@code ORGANIZATION_NAME_INVALID}, {@code ORGANIZATION_CONTACT_INVALID}. */
    private void describe(String name, String contactPhone, String contactEmail, Instant now) {
        this.name = Texts.required(name, MAX_NAME, "ORGANIZATION_NAME_INVALID");
        this.contactPhone = Texts.optional(contactPhone, 20, "ORGANIZATION_CONTACT_INVALID");
        this.contactEmail = Texts.optional(contactEmail, 254, "ORGANIZATION_CONTACT_INVALID");
        this.updatedAt = now;
    }

    /** Identifier of the organization. */
    public OrganizationId id() {
        return new OrganizationId(id);
    }

    /** Kind of client. */
    public OrganizationType type() {
        return type;
    }

    /** Name of the organization. */
    public String name() {
        return name;
    }

    /** ISO code of the organization's country (A1, M4, M5). */
    public String countryCode() {
        return countryCode;
    }

    /** Whether the organization holds a single site (type {@code RESIDENCE}). */
    public boolean singleSite() {
        return type == OrganizationType.RESIDENCE;
    }

    /** Optional contact phone, free text. */
    public Optional<String> contactPhone() {
        return Optional.ofNullable(contactPhone);
    }

    /** Optional contact email. */
    public Optional<String> contactEmail() {
        return Optional.ofNullable(contactEmail);
    }

    /** Whether the organization is a current client. */
    public boolean active() {
        return active;
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }
}
