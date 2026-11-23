package com.almaraama.identity.domain;

import com.almaraama.identity.api.AccountStatus;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.i18n.Language;
import com.almaraama.shared.id.PersonId;
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
 * A person and their account (D1). The e-mail is the sign-in identifier, verified by a link; the phone is mandatory
 * but not an identifier (ADR-030). Never deleted: deactivated, with history kept (D7, M2).
 */
@Entity
@Table(schema = "identity", name = "person")
public class Person {

    private static final int MAX_NAME_LENGTH = 100;

    @Id
    private UUID id;

    @Column(name = "first_name", nullable = false)
    private String firstName;

    @Column(name = "last_name", nullable = false)
    private String lastName;

    @Column(name = "email", nullable = false, unique = true)
    private String email;

    @Column(name = "email_verified_at")
    private Instant emailVerifiedAt;

    @Column(name = "phone", nullable = false)
    private String phone;

    @Enumerated(EnumType.STRING)
    @Column(name = "language", nullable = false)
    private Language language;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false)
    private AccountStatus status;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected Person() {
    }

    /** An account created by a manager, waiting for its "set my password" link to be used (D4). */
    public static Person invite(String firstName, String lastName, EmailAddress email, PhoneNumber phone,
                                Language language, Instant now) {
        return create(firstName, lastName, email, phone, language, AccountStatus.INVITED, now);
    }

    /**
     * An account whose owner chose a password and must now verify their e-mail before signing in (D1,
     * self-registration in sprint 2).
     */
    public static Person register(String firstName, String lastName, EmailAddress email, PhoneNumber phone,
                                  Language language, Instant now) {
        return create(firstName, lastName, email, phone, language, AccountStatus.PENDING_VERIFICATION, now);
    }

    /** Builds a new account in the given status. */
    private static Person create(String firstName, String lastName, EmailAddress email, PhoneNumber phone,
                                 Language language, AccountStatus status, Instant now) {
        Person person = new Person();
        person.id = PersonId.newId().value();
        person.firstName = name(firstName);
        person.lastName = name(lastName);
        person.email = email.value();
        person.phone = phone.value();
        person.language = language;
        person.status = status;
        person.createdAt = now;
        person.updatedAt = now;
        return person;
    }

    /**
     * The person proved they read this mailbox (verification, "set my password" or reset link, D1): the e-mail is
     * verified and an account waiting for it becomes active. Returns {@code true} when the account was activated by
     * this call. A deactivated account stays deactivated (D7).
     */
    public boolean verifyEmail(Instant now) {
        if (emailVerifiedAt == null) {
            emailVerifiedAt = now;
            updatedAt = now;
        }
        if (status == AccountStatus.INVITED || status == AccountStatus.PENDING_VERIFICATION) {
            status = AccountStatus.ACTIVE;
            updatedAt = now;
            return true;
        }
        return false;
    }

    /** Whether the person can sign in. */
    public boolean isActive() {
        return status == AccountStatus.ACTIVE;
    }

    /** Whether the account is deactivated (D7). */
    public boolean isDeactivated() {
        return status == AccountStatus.DEACTIVATED;
    }

    /** Identifier of the person. */
    public PersonId id() {
        return new PersonId(id);
    }

    /** First name, as entered. */
    public String firstName() {
        return firstName;
    }

    /** Last name, as entered. */
    public String lastName() {
        return lastName;
    }

    /** The e-mail, the sign-in identifier (D1). */
    public EmailAddress email() {
        return new EmailAddress(email);
    }

    /** When the e-mail was verified, if it was. */
    public Optional<Instant> emailVerifiedAt() {
        return Optional.ofNullable(emailVerifiedAt);
    }

    /** The mobile number in E.164 (D1, ADR-028); mandatory, not an identifier. */
    public PhoneNumber phone() {
        return new PhoneNumber(phone);
    }

    /** Interface language of the person (M1), also the language of their e-mails. */
    public Language language() {
        return language;
    }

    /** Invited, waiting for its verification, active or deactivated (D7). */
    public AccountStatus status() {
        return status;
    }

    /** Trims a name; {@code PERSON_NAME_INVALID} when empty or above 100 characters. */
    private static String name(String value) {
        String stripped = value == null ? "" : value.strip();
        if (stripped.isEmpty() || stripped.length() > MAX_NAME_LENGTH) {
            throw new BusinessException("PERSON_NAME_INVALID", ErrorCategory.VALIDATION, "Name required, 100 max");
        }
        return stripped;
    }
}
