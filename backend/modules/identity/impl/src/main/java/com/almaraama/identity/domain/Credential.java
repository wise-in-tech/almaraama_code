package com.almaraama.identity.domain;

import com.almaraama.shared.id.PersonId;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

/**
 * The password hash of a person and its lockout state (D5, ADR-030). The password itself is never stored. The hash
 * is empty only for development accounts migrated from the PIN, which must reset their password.
 */
@Entity
@Table(schema = "identity", name = "credential")
public class Credential {

    @Id
    @Column(name = "person_id")
    private UUID personId;

    @Column(name = "password_hash")
    private String passwordHash;

    @Column(name = "failed_attempts", nullable = false)
    private int failedAttempts;

    @Column(name = "locked_until")
    private Instant lockedUntil;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected Credential() {
    }

    /** The credential of a person who just chose their password, without failures. */
    public static Credential create(PersonId personId, String passwordHash, Instant now) {
        Credential credential = new Credential();
        credential.personId = personId.value();
        credential.passwordHash = passwordHash;
        credential.updatedAt = now;
        return credential;
    }

    /** A new password (set or reset, D4, D5): replaces the hash and clears the failures and the lock. */
    public void changePassword(String newHash, Instant now) {
        passwordHash = newHash;
        failedAttempts = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    /** Whether sign-in is locked at this time after too many failures. */
    public boolean isLocked(Instant now) {
        return lockedUntil != null && now.isBefore(lockedUntil);
    }

    /** Records a wrong password. Returns the lock end when this failure starts a lockout. */
    public Optional<Instant> recordFailure(Instant now) {
        failedAttempts++;
        updatedAt = now;
        Duration lock = LockoutPolicy.lockAfter(failedAttempts);
        if (lock.isZero()) {
            return Optional.empty();
        }
        lockedUntil = now.plus(lock);
        return Optional.of(lockedUntil);
    }

    /** Resets the failure counter and the lock after a successful sign-in. */
    public void recordSuccess(Instant now) {
        failedAttempts = 0;
        lockedUntil = null;
        updatedAt = now;
    }

    /** The person this password belongs to. */
    public PersonId personId() {
        return new PersonId(personId);
    }

    /** The Argon2id hash of the password, empty for a migrated PIN account. */
    public Optional<String> passwordHash() {
        return Optional.ofNullable(passwordHash);
    }

    /** Consecutive failed sign-ins since the last success. */
    public int failedAttempts() {
        return failedAttempts;
    }

    /** End of the current lock, if any. */
    public Optional<Instant> lockedUntil() {
        return Optional.ofNullable(lockedUntil);
    }
}
