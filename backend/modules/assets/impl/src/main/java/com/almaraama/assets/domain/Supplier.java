package com.almaraama.assets.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/** A supplier contact of an organization, reusable on many equipment (G3). Deactivated, never deleted (M2). */
@Entity
@Table(schema = "assets", name = "supplier")
public class Supplier {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "name", nullable = false)
    private String name;

    @Column(name = "phone")
    private String phone;

    @Column(name = "email")
    private String email;

    @Column(name = "activity")
    private String activity;

    @Column(name = "active", nullable = false)
    private boolean active;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected Supplier() {
    }

    /**
     * A new contact. {@code phone} is already in E.164 (parsed by the use case); {@code SUPPLIER_INVALID} for a missing
     * name or a too long text.
     */
    public static Supplier create(OrganizationId organization, String name, String phone, String email,
                                  String activity, Instant now) {
        Supplier supplier = new Supplier();
        supplier.id = UuidV7.next();
        supplier.organizationId = organization.value();
        supplier.createdAt = now;
        supplier.update(name, phone, email, activity, true, now);
        return supplier;
    }

    /** Changes or deactivates the contact. {@code SUPPLIER_INVALID}. */
    public void update(String name, String phone, String email, String activity, boolean active, Instant now) {
        this.name = Labels.required(name, 120, "SUPPLIER_INVALID");
        this.phone = Labels.optional(phone, 20, "SUPPLIER_INVALID");
        this.email = Labels.optional(email, 254, "SUPPLIER_INVALID");
        if (this.email != null && !this.email.contains("@")) {
            throw new BusinessException("SUPPLIER_INVALID", ErrorCategory.VALIDATION, "E-mail");
        }
        this.activity = Labels.optional(activity, 120, "SUPPLIER_INVALID");
        this.active = active;
        this.updatedAt = now;
    }

    /** Identifier. */
    public UUID id() {
        return id;
    }

    /** The owner organization. */
    public OrganizationId organizationId() {
        return new OrganizationId(organizationId);
    }

    /** Name of the company or the person. */
    public String name() {
        return name;
    }

    /** Phone in E.164. */
    public Optional<String> phone() {
        return Optional.ofNullable(phone);
    }

    /** E-mail. */
    public Optional<String> email() {
        return Optional.ofNullable(email);
    }

    /** Activity (elevators, plumbing…). */
    public Optional<String> activity() {
        return Optional.ofNullable(activity);
    }

    /** Whether it may be given to equipment. */
    public boolean active() {
        return active;
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }
}
