package com.almaraama.assets.domain;

import java.security.SecureRandom;

/**
 * QR codes of equipment (G4, ADR-039): 128 random bits written as 26 base32 characters ({@code A-Z2-7}). Not a secret
 * (labels hang in common areas): reading one needs a session and the equipment in the reader's perimeter.
 */
public final class QrCodes {

    private static final char[] ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZ234567".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private QrCodes() {
    }

    /** A new random code. */
    public static String next() {
        byte[] bytes = new byte[17];
        RANDOM.nextBytes(bytes);
        StringBuilder code = new StringBuilder(26);
        int buffer = 0;
        int bits = 0;
        for (byte b : bytes) {
            buffer = (buffer << 8) | (b & 0xFF);
            bits += 8;
            while (bits >= 5 && code.length() < 26) {
                code.append(ALPHABET[(buffer >> (bits - 5)) & 0x1F]);
                bits -= 5;
            }
        }
        return code.toString();
    }

    /** Whether a text has the shape of a code (before any lookup). */
    public static boolean wellFormed(String code) {
        return code != null && code.matches("^[A-Z2-7]{26}$");
    }
}
