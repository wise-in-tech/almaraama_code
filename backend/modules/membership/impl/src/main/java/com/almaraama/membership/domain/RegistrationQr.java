package com.almaraama.membership.domain;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * The registration QR code of a site (D2, ADR-034): a 10-character code printed as a QR code at the entrance, managed
 * by the promoteur (replace, activate, deactivate). The list of sites is never public: only someone holding the code
 * reaches the site's registration form. Never deleted (M2).
 */
@Entity
@Table(schema = "membership", name = "registration_qr")
public class RegistrationQr {

    static final int LENGTH = 10;

    @Id
    @Column(name = "site_id")
    private UUID siteId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "code", nullable = false, unique = true)
    private String code;

    @Column(name = "enabled", nullable = false)
    private boolean enabled;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected RegistrationQr() {
    }

    /** A new, active code for the site. */
    public static RegistrationQr create(OrganizationId organizationId, SiteId siteId, Instant now) {
        RegistrationQr qr = new RegistrationQr();
        qr.siteId = siteId.value();
        qr.organizationId = organizationId.value();
        qr.code = SecretCodes.generate(LENGTH);
        qr.enabled = true;
        qr.updatedAt = now;
        return qr;
    }

    /** D2: replaces the code; the old one stops working at once. The new code is active. */
    public void rotate(Instant now) {
        code = SecretCodes.generate(LENGTH);
        enabled = true;
        updatedAt = now;
    }

    /** D2: activates or deactivates registration on the site; a deactivated code answers like an unknown one. */
    public void enable(boolean value, Instant now) {
        enabled = value;
        updatedAt = now;
    }

    /** What the person typed, in the stored form (no dashes nor spaces, upper case). */
    public static String normalize(String typed) {
        return SecretCodes.normalize(typed);
    }

    /** The code in groups, for display and printing ({@code K7QM-4XPA-2R}). */
    public String display() {
        return SecretCodes.display(code);
    }

    /** The site the code opens. */
    public SiteId siteId() {
        return new SiteId(siteId);
    }

    /** The organization owning the site. */
    public OrganizationId organizationId() {
        return new OrganizationId(organizationId);
    }

    /** The stored form, without dashes. */
    public String code() {
        return code;
    }

    /** Whether registration is open. */
    public boolean enabled() {
        return enabled;
    }

    /** When the code was last created, replaced, activated or deactivated. */
    public Instant updatedAt() {
        return updatedAt;
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }
}
