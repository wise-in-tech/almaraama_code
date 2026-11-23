package com.almaraama.identity.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;

/**
 * Secrets given to a person (invitation links, refresh tokens): 256 random bits. Only their SHA-256 hash is stored,
 * so a copy of the database gives no usable link or session.
 */
public final class SecretTokens {

    private static final SecureRandom RANDOM = new SecureRandom();

    /** Static helpers only. */
    private SecretTokens() {
    }

    /** A new secret of 256 random bits, URL-safe Base64. */
    public static String generate() {
        byte[] bytes = new byte[32];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** SHA-256 of a secret, hexadecimal: what the database stores. */
    public static String hash(String token) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256").digest(token.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
