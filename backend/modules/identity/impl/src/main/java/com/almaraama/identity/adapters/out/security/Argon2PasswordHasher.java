package com.almaraama.identity.adapters.out.security;

import com.almaraama.identity.domain.PasswordHasher;
import org.springframework.security.crypto.argon2.Argon2PasswordEncoder;
import org.springframework.stereotype.Component;

/**
 * Argon2id with the OWASP recommended parameters (ADR-030): 19 MiB of memory, 2 iterations, parallelism 1, 16-byte
 * salt. Changing them later is safe: existing hashes carry their own parameters.
 */
@Component
class Argon2PasswordHasher implements PasswordHasher {

    private static final int SALT_LENGTH = 16;
    private static final int HASH_LENGTH = 32;
    private static final int PARALLELISM = 1;
    private static final int MEMORY_KIB = 19_456;
    private static final int ITERATIONS = 2;

    private final Argon2PasswordEncoder encoder =
            new Argon2PasswordEncoder(SALT_LENGTH, HASH_LENGTH, PARALLELISM, MEMORY_KIB, ITERATIONS);
    private final String decoyHash = encoder.encode("decoy-password-never-valid");

    /** Argon2id hash of the password, with a fresh salt; the parameters are stored in the hash itself. */
    @Override
    public String hash(String normalizedPassword) {
        return encoder.encode(normalizedPassword);
    }

    /** Whether the typed password matches the stored hash; {@code false} for a missing password or hash. */
    @Override
    public boolean matches(String normalizedPassword, String hash) {
        return normalizedPassword != null && hash != null && encoder.matches(normalizedPassword, hash);
    }

    /** Spends the time of a real check on a decoy hash, so an unknown e-mail is not revealed by timing. */
    @Override
    public void matchAgainstDecoy(String normalizedPassword) {
        encoder.matches(normalizedPassword == null ? "" : normalizedPassword, decoyHash);
    }
}
