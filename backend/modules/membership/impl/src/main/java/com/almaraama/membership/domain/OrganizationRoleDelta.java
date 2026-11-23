package com.almaraama.membership.domain;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.access.RoleCode;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
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
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A difference of one organization from the default permissions of one role (C9, C11, ADR-026 revision 2): {@code
 * ADD} a permission the role does not have by default, or {@code REMOVE} one it has. Only differences are stored, so a
 * permission added to the defaults by a later phase reaches the role unless the organization removed it. Cancelled,
 * never deleted (M2).
 */
@Entity
@Table(schema = "membership", name = "organization_role_delta")
public class OrganizationRoleDelta {

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "role_code", nullable = false)
    private String roleCode;

    @Convert(converter = PermissionSetConverter.class)
    @Column(name = "permission", nullable = false)
    private Set<Permission> permission;

    @Enumerated(EnumType.STRING)
    @Column(name = "effect", nullable = false)
    private AdjustmentEffect effect;

    @Column(name = "reason")
    private String reason;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected OrganizationRoleDelta() {
    }

    /** A new active difference; the caller checked that the role is editable and the permission adjustable. */
    public static OrganizationRoleDelta of(OrganizationId organization, RoleCode role, Permission permission,
                                           AdjustmentEffect effect, String reason, PersonId by, Instant now) {
        OrganizationRoleDelta delta = new OrganizationRoleDelta();
        delta.id = UuidV7.next();
        delta.organizationId = organization.value();
        delta.roleCode = role.value();
        delta.permission = Set.of(permission);
        delta.effect = effect;
        delta.reason = reason == null || reason.isBlank() ? null : reason.strip();
        delta.createdBy = by.value();
        delta.createdAt = now;
        return delta;
    }

    /** "Back to defaults" or a changed choice: the difference stops applying, its history stays (M2). */
    public void cancel(PersonId by, Instant now) {
        if (cancelledAt == null) {
            cancelledAt = now;
            cancelledBy = by.value();
        }
    }

    /** Identifier of the difference. */
    public UUID id() {
        return id;
    }

    /** The organization it applies to. */
    public OrganizationId organizationId() {
        return new OrganizationId(organizationId);
    }

    /** The role it changes. */
    public RoleCode roleCode() {
        return new RoleCode(roleCode);
    }

    /**
     * The permission added or removed; empty when the catalogue no longer knows its code (a later version removed
     * it).
     */
    public Optional<Permission> permission() {
        return permission.stream().findFirst();
    }

    /** {@code ADD} or {@code REMOVE}. */
    public AdjustmentEffect effect() {
        return effect;
    }

    /** Who created it. */
    public PersonId createdBy() {
        return new PersonId(createdBy);
    }

    /** When it was created. */
    public Instant createdAt() {
        return createdAt;
    }

    /** Whether it still applies. */
    public boolean active() {
        return cancelledAt == null;
    }
}
