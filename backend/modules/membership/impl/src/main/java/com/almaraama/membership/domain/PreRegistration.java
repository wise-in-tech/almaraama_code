package com.almaraama.membership.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.i18n.Language;
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
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * A person expected on a site, with the units planned for them and a personal key (D3, ADR-034). When the person
 * registers with the site's QR code, the key and the same e-mail, the request is approved automatically with the
 * planned units. The key is single use, expires ({@code keyValidDays}, 30 by default), and is stored only as its
 * SHA-256 hash: it is shown once, at creation or renewal. Never deleted (M2).
 */
@Entity
@Table(schema = "membership", name = "pre_registration")
public class PreRegistration {

    public static final int KEY_LENGTH = 12;
    public static final int DEFAULT_VALID_DAYS = 30;
    public static final int MAX_VALID_DAYS = 90;
    public static final int MAX_PLANNED_UNITS = 50;
    private static final int MAX_NAME = 100;

    /** A pre-registration just created or renewed, with its key in clear: give it to the person, never store it. */
    public record Issued(PreRegistration preRegistration, String key) {

        /** Never prints the key. */
        @Override
        public String toString() {
            return "Issued[" + preRegistration.id + "]";
        }
    }

    @Id
    private UUID id;

    @Column(name = "organization_id", nullable = false)
    private UUID organizationId;

    @Column(name = "site_id", nullable = false)
    private UUID siteId;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    /** Normalized e-mail (trimmed, lower case), compared with the account's at registration. */
    @Column(name = "email", nullable = false)
    private String email;

    /** E.164. */
    @Column(name = "phone", nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false)
    private Language language;

    @Convert(converter = PlannedOccupanciesConverter.class)
    @Column(name = "planned_occupancies", nullable = false)
    private List<PlannedOccupancy> plannedOccupancies;

    @Column(name = "key_hash", nullable = false, unique = true)
    private String keyHash;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private PreRegistrationStatus status;

    @Enumerated(EnumType.STRING)
    @Column(name = "origin", nullable = false)
    private PreRegistrationOrigin origin;

    @Column(name = "created_by", nullable = false)
    private UUID createdBy;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Column(name = "used_by_person_id")
    private UUID usedByPersonId;

    @Column(name = "membership_id")
    private UUID membershipId;

    @Column(name = "cancelled_by")
    private UUID cancelledBy;

    @Column(name = "cancelled_at")
    private Instant cancelledAt;

    @Column(name = "cancel_reason")
    private String cancelReason;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected PreRegistration() {
    }

    /**
     * A new pending pre-registration with a fresh key. The caller normalized the e-mail and phone (identity module)
     * and checked the units against E3 and E4. Errors: {@code PRE_REGISTRATION_NAME_INVALID}, {@code
     * PRE_REGISTRATION_UNITS_INVALID} (1 to 50 distinct units), {@code PRE_REGISTRATION_VALIDITY_INVALID} (1 to 90
     * days).
     */
    public static Issued create(OrganizationId organizationId, SiteId siteId, String firstName, String lastName,
                                String email, String phone, Language language, List<PlannedOccupancy> planned,
                                int validDays, PreRegistrationOrigin origin, PersonId by, Instant now) {
        Set<UUID> distinct = new HashSet<>();
        if (planned.isEmpty() || planned.size() > MAX_PLANNED_UNITS
                || !planned.stream().allMatch(p -> distinct.add(p.unitId().value()))) {
            throw new BusinessException("PRE_REGISTRATION_UNITS_INVALID", ErrorCategory.VALIDATION,
                    "1 to 50 distinct units");
        }
        PreRegistration pre = new PreRegistration();
        pre.id = UuidV7.next();
        pre.organizationId = organizationId.value();
        pre.siteId = siteId.value();
        pre.firstName = name(firstName);
        pre.lastName = name(lastName);
        pre.email = email;
        pre.phone = phone;
        pre.language = language;
        pre.plannedOccupancies = new ArrayList<>(planned);
        pre.status = PreRegistrationStatus.PENDING;
        pre.origin = origin;
        pre.createdBy = by.value();
        pre.createdAt = now;
        String key = pre.newKey(validDays, now);
        return new Issued(pre, key);
    }

    /**
     * D3: a new key for a pending pre-registration (lost key); the old one stops working. An expired one can be
     * renewed. {@code PRE_REGISTRATION_NOT_PENDING} once used or cancelled.
     */
    public String renewKey(int validDays, Instant now) {
        requirePending();
        return newKey(validDays, now);
    }

    /** Cancels a pending (or expired) pre-registration; {@code PRE_REGISTRATION_NOT_PENDING} otherwise. */
    public void cancel(PersonId by, String reason, Instant now) {
        requirePending();
        status = PreRegistrationStatus.CANCELLED;
        cancelledBy = by.value();
        cancelledAt = now;
        cancelReason = reason == null || reason.isBlank() ? null : reason.strip();
        updatedAt = now;
    }

    /**
     * D3: whether a person registering on this site with this (normalized) e-mail may use this pre-registration: it
     * is still pending and not expired, and the e-mail is the planned one. The caller already found it by the key.
     */
    public boolean matches(SiteId site, String normalizedEmail, Instant now) {
        return status(now) == PreRegistrationStatus.PENDING && siteId.equals(site.value())
                && email.equals(normalizedEmail);
    }

    /** D3: the key was used by this person for this access; it never works again. */
    public void use(PersonId person, UUID membership, Instant now) {
        requirePending();
        status = PreRegistrationStatus.USED;
        usedAt = now;
        usedByPersonId = person.value();
        membershipId = membership;
        updatedAt = now;
    }

    /** Draws a new key, stores its hash and the new expiry, and returns the key in clear. */
    private String newKey(int validDays, Instant now) {
        if (validDays < 1 || validDays > MAX_VALID_DAYS) {
            throw new BusinessException("PRE_REGISTRATION_VALIDITY_INVALID", ErrorCategory.VALIDATION,
                    "1 to 90 days");
        }
        String key = SecretCodes.generate(KEY_LENGTH);
        keyHash = SecretCodes.hash(key);
        expiresAt = now.plus(Duration.ofDays(validDays));
        updatedAt = now;
        return SecretCodes.display(key);
    }

    /** {@code PRE_REGISTRATION_NOT_PENDING} once used or cancelled. */
    private void requirePending() {
        if (status != PreRegistrationStatus.PENDING) {
            throw new BusinessException("PRE_REGISTRATION_NOT_PENDING", ErrorCategory.RULE_VIOLATION,
                    "Used or cancelled");
        }
    }

    /** A first or last name, 1 to 100 characters; {@code PRE_REGISTRATION_NAME_INVALID}. */
    private static String name(String value) {
        String clean = value == null ? "" : value.strip();
        if (clean.isEmpty() || clean.length() > MAX_NAME) {
            throw new BusinessException("PRE_REGISTRATION_NAME_INVALID", ErrorCategory.VALIDATION,
                    "Name of 1 to 100 characters");
        }
        return clean;
    }

    /** Identifier of the pre-registration. */
    public UUID id() {
        return id;
    }

    /** The organization owning the site. */
    public OrganizationId organizationId() {
        return new OrganizationId(organizationId);
    }

    /** The site the person is expected on. */
    public SiteId siteId() {
        return new SiteId(siteId);
    }

    /** First name. */
    public String firstName() {
        return firstName;
    }

    /** Last name. */
    public String lastName() {
        return lastName;
    }

    /** Normalized e-mail. */
    public String email() {
        return email;
    }

    /** Phone in E.164. */
    public String phone() {
        return phone;
    }

    /** Language of the person. */
    public Language language() {
        return language;
    }

    /** The units planned for the person. */
    public List<PlannedOccupancy> plannedOccupancies() {
        return List.copyOf(plannedOccupancies);
    }

    /** The stored status, or {@code EXPIRED} for a pending one past its expiry. */
    public PreRegistrationStatus status(Instant now) {
        if (status == PreRegistrationStatus.PENDING && !now.isBefore(expiresAt)) {
            return PreRegistrationStatus.EXPIRED;
        }
        return status;
    }

    /** Typed by hand or imported. */
    public PreRegistrationOrigin origin() {
        return origin;
    }

    /** When the current key stops working. */
    public Instant expiresAt() {
        return expiresAt;
    }

    /** Who created it: the decision of an automatic approval is recorded in their name. */
    public PersonId createdBy() {
        return new PersonId(createdBy);
    }

    /** When it was created. */
    public Instant createdAt() {
        return createdAt;
    }

    /** When the key was used. */
    public Optional<Instant> usedAt() {
        return Optional.ofNullable(usedAt);
    }

    /** The access created when the key was used. */
    public Optional<UUID> membershipId() {
        return Optional.ofNullable(membershipId);
    }

    /** When it was cancelled. */
    public Optional<Instant> cancelledAt() {
        return Optional.ofNullable(cancelledAt);
    }

    /** Why it was cancelled. */
    public Optional<String> cancelReason() {
        return Optional.ofNullable(cancelReason);
    }

    /** Optimistic lock version, 0 before the first save. */
    public long version() {
        return version == null ? 0 : version;
    }
}
