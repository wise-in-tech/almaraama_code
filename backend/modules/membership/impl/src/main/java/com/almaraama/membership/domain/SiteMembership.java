package com.almaraama.membership.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.BuildingId;
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
import java.util.LinkedHashSet;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * A person's access to a site (D2, D3, D5, C7): the resident belongs to the site. Status follows the UML state
 * diagram 5.2. An active access comes with the {@code RESIDENT} role on the site. Never deleted (D6, M2).
 */
@Entity
@Table(schema = "membership", name = "site_membership")
public class SiteMembership {

    /** Longest free comment of a registration (D2). */
    public static final int MAX_COMMENT = 500;

    @Id
    private UUID id;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private MembershipStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false)
    private MembershipOrigin origin;

    @Convert(converter = UuidSetConverter.class)
    @Column(name = "allowed_building_ids", nullable = false)
    private Set<UUID> allowedBuildingIds;

    /** Free comment of the person (D2); personal data, never logged. */
    @Column(name = "comment")
    private String comment;

    /** When the request reached the managers: e-mail verified (ADR-034); {@code null} until then. */
    @Column(name = "submitted_at")
    private Instant submittedAt;

    /** SHA-256 of the pre-registration key typed, until it is checked at submission; never the key itself. */
    @Column(name = "key_hash")
    private String keyHash;

    @Enumerated(EnumType.STRING)
    @Column(name = "key_outcome", nullable = false)
    private KeyOutcome keyOutcome;

    @Column(name = "pre_registration_id")
    private UUID preRegistrationId;

    @Column(name = "decided_by")
    private UUID decidedBy;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decision_reason")
    private String decisionReason;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "ended_at")
    private Instant endedAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected SiteMembership() {
    }

    /**
     * D2: invited by a manager; active once the person chooses their password, or at once if they already have an
     * account.
     */
    public static SiteMembership invited(PersonId personId, OrganizationId organizationId, SiteId siteId,
                                         Set<BuildingId> allowedBuildings, boolean accountActive, Instant now) {
        SiteMembership membership = create(personId, organizationId, siteId, MembershipOrigin.INVITATION, now);
        membership.allowedBuildingIds = ids(allowedBuildings);
        membership.status = accountActive ? MembershipStatus.ACTIVE : MembershipStatus.PENDING;
        return membership;
    }

    /**
     * D2: a request by registration QR code, pending until someone with {@code access:approve} decides. {@code
     * submitted} is {@code false} for a new account whose e-mail is not verified yet (ADR-034): the request then
     * reaches nobody until {@link #submit}. {@code COMMENT_TOO_LONG} beyond 500 characters.
     *
     * @param keyHash SHA-256 of the pre-registration key typed, or {@code null}
     */
    public static SiteMembership requested(PersonId personId, OrganizationId organizationId, SiteId siteId,
                                           String comment, String keyHash, boolean submitted, Instant now) {
        String cleanComment = checkComment(comment);
        SiteMembership membership = create(personId, organizationId, siteId, MembershipOrigin.SELF_REGISTRATION,
                now);
        membership.comment = cleanComment;
        membership.keyHash = keyHash;
        membership.status = MembershipStatus.PENDING;
        membership.submittedAt = submitted ? now : null;
        return membership;
    }

    /**
     * The comment as stored: trimmed, {@code null} when blank; {@code COMMENT_TOO_LONG} beyond 500 characters (D2).
     * Called before anything else is created, so that a refused comment creates no account.
     */
    public static String checkComment(String comment) {
        String clean = comment == null || comment.isBlank() ? null : comment.strip();
        if (clean != null && clean.length() > MAX_COMMENT) {
            throw new BusinessException("COMMENT_TOO_LONG", ErrorCategory.VALIDATION, "500 characters at most");
        }
        return clean;
    }

    /** ADR-034: the e-mail is verified, the request reaches the managers; {@code false} when already submitted. */
    public boolean submit(Instant now) {
        if (submittedAt != null || status != MembershipStatus.PENDING) {
            return false;
        }
        submittedAt = now;
        updatedAt = now;
        return true;
    }

    /**
     * D3: records what the typed key gave and forgets its hash (it is checked only once). {@code preRegistration}
     * is the pre-registration that matched, if any.
     */
    public void keyChecked(KeyOutcome outcome, UUID preRegistration, Instant now) {
        keyOutcome = outcome;
        preRegistrationId = preRegistration;
        keyHash = null;
        updatedAt = now;
    }

    /**
     * D3: approved automatically by a matching pre-registration key, with the buildings of the planned units (C7);
     * the decision is recorded in the name of the person who created the pre-registration.
     */
    public void approveWithKey(Set<BuildingId> allowedBuildings, PersonId preRegisteredBy, Instant now) {
        approve(allowedBuildings, preRegisteredBy, now);
        origin = MembershipOrigin.PRE_REGISTRATION;
    }

    /** Common start of an access, without status. */
    private static SiteMembership create(PersonId personId, OrganizationId organizationId, SiteId siteId,
                                         MembershipOrigin origin, Instant now) {
        SiteMembership membership = new SiteMembership();
        membership.id = UuidV7.next();
        membership.personId = personId.value();
        membership.organizationId = organizationId.value();
        membership.siteId = siteId.value();
        membership.origin = origin;
        membership.allowedBuildingIds = new LinkedHashSet<>();
        membership.keyOutcome = KeyOutcome.NONE;
        membership.createdAt = now;
        membership.updatedAt = now;
        return membership;
    }

    /** The invited person chose their password (D2, D4). */
    public boolean activateInvitation(Instant now) {
        if (status != MembershipStatus.PENDING || origin != MembershipOrigin.INVITATION) {
            return false;
        }
        status = MembershipStatus.ACTIVE;
        updatedAt = now;
        return true;
    }

    /** D2 step 3: the access opens to the chosen buildings only (empty means every building, C7). */
    public void approve(Set<BuildingId> allowedBuildings, PersonId by, Instant now) {
        requirePending();
        allowedBuildingIds = ids(allowedBuildings);
        status = MembershipStatus.ACTIVE;
        decide(by, null, now);
    }

    /** Rejects a pending request; {@code MEMBERSHIP_NOT_PENDING}. */
    public void reject(PersonId by, String reason, Instant now) {
        requirePending();
        status = MembershipStatus.REJECTED;
        decide(by, reason, now);
    }

    /** Sets the buildings the resident may see (C7); empty means every building. */
    public void restrictTo(Set<BuildingId> allowedBuildings, Instant now) {
        allowedBuildingIds = ids(allowedBuildings);
        updatedAt = now;
    }

    /** Ends an active access, kept in the history; {@code MEMBERSHIP_NOT_ACTIVE}. */
    public void deactivate(PersonId by, String reason, Instant now) {
        if (status != MembershipStatus.ACTIVE) {
            throw new BusinessException("MEMBERSHIP_NOT_ACTIVE", ErrorCategory.RULE_VIOLATION, "Not active");
        }
        status = MembershipStatus.DEACTIVATED;
        endedAt = now;
        decide(by, reason, now);
    }

    /** Gives back a deactivated access; {@code MEMBERSHIP_NOT_DEACTIVATED}. */
    public void reactivate(PersonId by, Instant now) {
        if (status != MembershipStatus.DEACTIVATED) {
            throw new BusinessException("MEMBERSHIP_NOT_DEACTIVATED", ErrorCategory.RULE_VIOLATION,
                    "Not deactivated");
        }
        status = MembershipStatus.ACTIVE;
        endedAt = null;
        decide(by, null, now);
    }

    /** {@code MEMBERSHIP_NOT_PENDING} unless still waiting for a decision. */
    private void requirePending() {
        if (status != MembershipStatus.PENDING) {
            throw new BusinessException("MEMBERSHIP_NOT_PENDING", ErrorCategory.RULE_VIOLATION, "Not pending");
        }
    }

    /** Records who decided, when and why. */
    private void decide(PersonId by, String reason, Instant now) {
        decidedBy = by.value();
        decidedAt = now;
        decisionReason = reason == null || reason.isBlank() ? null : reason.strip();
        updatedAt = now;
    }

    /** Raw identifiers of the buildings, in their given order. */
    private static Set<UUID> ids(Set<BuildingId> buildings) {
        return buildings.stream().map(BuildingId::value).collect(Collectors.toCollection(LinkedHashSet::new));
    }

    /** Identifier of the access. */
    public UUID id() {
        return id;
    }

    /** The person. */
    public PersonId personId() {
        return new PersonId(personId);
    }

    /** The organization owning the site. */
    public OrganizationId organizationId() {
        return new OrganizationId(organizationId);
    }

    /** The site. */
    public SiteId siteId() {
        return new SiteId(siteId);
    }

    /** Pending, active, rejected or deactivated. */
    public MembershipStatus status() {
        return status;
    }

    /** Invitation, self-registration or pre-registration key. */
    public MembershipOrigin origin() {
        return origin;
    }

    /** Buildings the resident may see; empty means every building (C7). */
    public Set<BuildingId> allowedBuildingIds() {
        return allowedBuildingIds.stream().map(BuildingId::new).collect(Collectors.toUnmodifiableSet());
    }

    /** Free comment of the person at registration (D2). */
    public Optional<String> comment() {
        return Optional.ofNullable(comment);
    }

    /** When the request reached the managers (ADR-034); empty while the e-mail is not verified. */
    public Optional<Instant> submittedAt() {
        return Optional.ofNullable(submittedAt);
    }

    /** SHA-256 of the typed key, until it is checked. */
    public Optional<String> keyHash() {
        return Optional.ofNullable(keyHash);
    }

    /** What the typed key gave (D3). */
    public KeyOutcome keyOutcome() {
        return keyOutcome;
    }

    /** The pre-registration whose key matched. */
    public Optional<UUID> preRegistrationId() {
        return Optional.ofNullable(preRegistrationId);
    }

    /** Who made the last decision. */
    public Optional<PersonId> decidedBy() {
        return Optional.ofNullable(decidedBy).map(PersonId::new);
    }

    /** When the last decision was made. */
    public Optional<Instant> decidedAt() {
        return Optional.ofNullable(decidedAt);
    }

    /** Reason of the last decision. */
    public Optional<String> decisionReason() {
        return Optional.ofNullable(decisionReason);
    }

    /** When the access was asked or invited. */
    public Instant createdAt() {
        return createdAt;
    }

    /** When the access was deactivated. */
    public Optional<Instant> endedAt() {
        return Optional.ofNullable(endedAt);
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }
}
