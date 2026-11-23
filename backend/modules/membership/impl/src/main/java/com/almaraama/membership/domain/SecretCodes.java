package com.almaraama.membership.domain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.util.HexFormat;
import java.util.Locale;

/**
 * Codes typed by people (ADR-034): registration codes of the sites (10 characters) and pre-registration keys (12
 * characters), drawn by {@link SecureRandom} from an alphabet without ambiguous letters (no 0, O, 1, I, L), shown in
 * groups of four, typed with or without dashes and in any case. A key is stored only as its SHA-256 hash.
 */
public final class SecretCodes {

    static final String ALPHABET = "23456789ABCDEFGHJKMNPQRSTUVWXYZ";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Static helpers only. */
    private SecretCodes() {
    }

    /** A new random code of this length (about 4.95 bits per character). */
    public static String generate(int length) {
        StringBuilder builder = new StringBuilder(length);
        for (int i = 0; i < length; i++) {
            builder.append(ALPHABET.charAt(RANDOM.nextInt(ALPHABET.length())));
        }
        return builder.toString();
    }

    /** What the person typed, without spaces and dashes, upper case; empty for {@code null}. */
    public static String normalize(String typed) {
        return typed == null ? "" : typed.replaceAll("[\\s-]", "").toUpperCase(Locale.ROOT);
    }

    /** The code in groups of four characters separated by dashes ({@code K7QM-4XPA-2R}). */
    public static String display(String code) {
        StringBuilder out = new StringBuilder();
        for (int i = 0; i < code.length(); i += 4) {
            if (i > 0) {
                out.append('-');
            }
            out.append(code, i, Math.min(code.length(), i + 4));
        }
        return out.toString();
    }

    /** SHA-256 of the normalized code, in lower-case hexadecimal: what is stored for a pre-registration key. */
    public static String hash(String typed) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(normalize(typed).getBytes(StandardCharsets.US_ASCII));
            return HexFormat.of().formatHex(digest);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is always available", e);
        }
    }
}
