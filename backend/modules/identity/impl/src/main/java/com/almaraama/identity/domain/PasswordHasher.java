package com.almaraama.identity.domain;

/** Out port: hashing and checking passwords (Argon2id in production, ADR-030). */
public interface PasswordHasher {

    /** Salted hash of a new password, already checked and normalized; the only form ever stored. */
    String hash(String normalizedPassword);

    /** Whether a typed password, normalized, matches a stored hash; {@code false} when there is no hash. */
    boolean matches(String normalizedPassword, String hash);

    /**
     * Spends the same time as a real check, for unknown e-mails, so the response time does not reveal whether an
     * account exists (ADR-030).
     */
    void matchAgainstDecoy(String normalizedPassword);
}
