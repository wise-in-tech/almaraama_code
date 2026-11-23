package com.almaraama.membership.domain;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
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
import java.util.EnumSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A permission added to one person, or one of their role's permissions removed for them only, on an organization
 * or one of its sites (C9, ADR-026). Cancelled, never deleted (M2).
 */
@Entity
@Table(schema = "membership", name = "permission_adjustment")
public class PermissionAdjustment {

    @Id
    private UUID id;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false)
    private ScopeType scopeType;

    @Column(name = "site_id")
    private UUID siteId;

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
    protected PermissionAdjustment() {
    }

    /** RB-3: the permission must apply to the scope. */
    public static PermissionAdjustment of(PersonId personId, OrganizationId organizationId, Optional<SiteId> siteId,
                                          Permission permission, AdjustmentEffect effect, String reason,
                                          PersonId by, Instant now) {
        ScopeType scope = siteId.isPresent() ? ScopeType.SITE : ScopeType.ORGANIZATION;
        if (!permission.appliesTo(scope)) {
            throw new BusinessException("PERMISSION_SCOPE_INVALID", ErrorCategory.RULE_VIOLATION,
                    permission.code() + " cannot be used on " + scope);
        }
        PermissionAdjustment adjustment = new PermissionAdjustment();
        adjustment.id = UuidV7.next();
        adjustment.personId = personId.value();
        adjustment.organizationId = organizationId.value();
        adjustment.scopeType = scope;
        adjustment.siteId = siteId.map(SiteId::value).orElse(null);
        adjustment.permission = EnumSet.of(permission);
        adjustment.effect = effect;
        adjustment.reason = reason == null || reason.isBlank() ? null : reason.strip();
        adjustment.createdBy = by.value();
        adjustment.createdAt = now;
        return adjustment;
    }

    /** Cancels the adjustment; {@code PERMISSION_ADJUSTMENT_ALREADY_CANCELLED}. */
    public void cancel(PersonId by, Instant now) {
        if (cancelledAt != null) {
            throw new BusinessException("PERMISSION_ADJUSTMENT_ALREADY_CANCELLED", ErrorCategory.RULE_VIOLATION,
                    "Already cancelled");
        }
        cancelledAt = now;
        cancelledBy = by.value();
    }

    /** Whether it is still in force. */
    public boolean active() {
        return cancelledAt == null;
    }

    /** Identifier of the adjustment. */
    public UUID id() {
        return id;
    }

    /** The person whose permissions change. */
    public PersonId personId() {
        return new PersonId(personId);
    }

    /** The organization. */
    public OrganizationId organizationId() {
        return new OrganizationId(organizationId);
    }

    /** The organization itself, or one of its sites. */
    public ScopeType scopeType() {
        return scopeType;
    }

    /** The site, for a site adjustment. */
    public Optional<SiteId> siteId() {
        return Optional.ofNullable(siteId).map(SiteId::new);
    }

    /** The permission added or removed; {@code null} when the catalogue no longer knows its code (ignored). */
    public Permission permission() {
        return permission.isEmpty() ? null : permission.iterator().next();
    }

    /** Added or removed. */
    public AdjustmentEffect effect() {
        return effect;
    }

    /** Why, as written by the ORG_ADMIN. */
    public Optional<String> reason() {
        return Optional.ofNullable(reason);
    }

    /** Who made it. */
    public PersonId createdBy() {
        return new PersonId(createdBy);
    }

    /** When it was made. */
    public Instant createdAt() {
        return createdAt;
    }

    /** Who cancelled it. */
    public Optional<PersonId> cancelledBy() {
        return Optional.ofNullable(cancelledBy).map(PersonId::new);
    }

    /** When it was cancelled. */
    public Optional<Instant> cancelledAt() {
        return Optional.ofNullable(cancelledAt);
    }
}
