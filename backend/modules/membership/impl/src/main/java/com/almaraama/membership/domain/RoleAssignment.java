package com.almaraama.membership.domain;

import com.almaraama.shared.access.RoleCode;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.actor.ActorType;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
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
import java.util.Optional;
import java.util.UUID;

/**
 * A role given to a person on a scope (C1, ADR-026): the platform, an organization (all its sites) or one site.
 * Ended, never deleted (M2).
 */
@Entity
@Table(schema = "membership", name = "role_assignment")
public class RoleAssignment {

    @Id
    private UUID id;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(name = "role", nullable = false)
    private String role;

    @Enumerated(EnumType.STRING)
    @Column(name = "scope_type", nullable = false)
    private ScopeType scopeType;

    @Column(name = "organization_id")
    private UUID organizationId;

    @Column(name = "site_id")
    private UUID siteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "assigned_by_type", nullable = false)
    private ActorType assignedByType;

    @Column(name = "assigned_by_id", nullable = false)
    private UUID assignedById;

    @Column(name = "assigned_at", nullable = false)
    private Instant assignedAt;

    @Column(name = "ended_by_id")
    private UUID endedById;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "end_reason")
    private String endReason;

    @Version
    private Long version;

    /** For JPA only. */
    protected RoleAssignment() {
    }

    /** A role on the platform itself (our team). */
    public static RoleAssignment onPlatform(PersonId personId, Role role, Actor by, Instant now) {
        return create(personId, role, ScopeType.PLATFORM, null, null, by, now);
    }

    /** A role on an organization and all its sites; {@code ROLE_SCOPE_INVALID} for a site-only role. */
    public static RoleAssignment onOrganization(PersonId personId, Role role, OrganizationId organizationId,
                                                Actor by, Instant now) {
        return create(personId, role, ScopeType.ORGANIZATION, organizationId.value(), null, by, now);
    }

    /** A role on one site; {@code ROLE_SCOPE_INVALID} for an organization-only role. */
    public static RoleAssignment onSite(PersonId personId, Role role, OrganizationId organizationId, SiteId siteId,
                                        Actor by, Instant now) {
        return create(personId, role, ScopeType.SITE, organizationId.value(), siteId.value(), by, now);
    }

    /** RB-3: a role is given only on a scope it declares. */
    private static RoleAssignment create(PersonId personId, Role role, ScopeType scope, UUID organizationId,
                                         UUID siteId, Actor by, Instant now) {
        if (!role.appliesTo(scope)) {
            throw new BusinessException("ROLE_SCOPE_INVALID", ErrorCategory.RULE_VIOLATION,
                    role.code() + " cannot be given on " + scope);
        }
        RoleAssignment assignment = new RoleAssignment();
        assignment.id = UuidV7.next();
        assignment.personId = personId.value();
        assignment.role = role.code().value();
        assignment.scopeType = scope;
        assignment.organizationId = organizationId;
        assignment.siteId = siteId;
        assignment.assignedByType = by.type();
        assignment.assignedById = by.id();
        assignment.assignedAt = now;
        return assignment;
    }

    /** Ends the assignment; {@code ROLE_ASSIGNMENT_ALREADY_ENDED}. */
    public void end(PersonId by, String reason, Instant now) {
        if (endedAt != null) {
            throw new BusinessException("ROLE_ASSIGNMENT_ALREADY_ENDED", ErrorCategory.RULE_VIOLATION, "Ended");
        }
        this.endedAt = now;
        this.endedById = by.value();
        this.endReason = reason == null || reason.isBlank() ? null : reason.strip();
    }

    /** Whether it is still in force. */
    public boolean active() {
        return endedAt == null;
    }

    /** Identifier of the assignment. */
    public UUID id() {
        return id;
    }

    /** The person holding the role. */
    public PersonId personId() {
        return new PersonId(personId);
    }

    /** The code of the role (a row of the role table). */
    public RoleCode role() {
        return new RoleCode(role);
    }

    /** Platform, organization or site. */
    public ScopeType scopeType() {
        return scopeType;
    }

    /** The organization, except for a platform role. */
    public Optional<OrganizationId> organizationId() {
        return Optional.ofNullable(organizationId).map(OrganizationId::new);
    }

    /** The site, for a site role. */
    public Optional<SiteId> siteId() {
        return Optional.ofNullable(siteId).map(SiteId::new);
    }

    /** Who gave the role (a person, or the platform at onboarding). */
    public Actor assignedBy() {
        return new Actor(assignedByType, assignedById);
    }

    /** When the role was given. */
    public Instant assignedAt() {
        return assignedAt;
    }

    /** Who took the role away. */
    public Optional<PersonId> endedBy() {
        return Optional.ofNullable(endedById).map(PersonId::new);
    }

    /** When the role was taken away. */
    public Optional<Instant> endedAt() {
        return Optional.ofNullable(endedAt);
    }

    /** Why the role was taken away. */
    public Optional<String> endReason() {
        return Optional.ofNullable(endReason);
    }
}
