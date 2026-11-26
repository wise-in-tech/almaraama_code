package com.almaraama.files.domain;

import java.util.Optional;

/**
 * The kinds of content the platform accepts, detected from the first bytes only (ADR-038): never from the file name
 * or the type announced by the browser.
 */
public enum ContentKind {

    /** JPEG image ({@code FF D8 FF}). */
    JPEG,

    /** PNG image ({@code 89 50 4E 47 0D 0A 1A 0A}). */
    PNG,

    /** PDF document ({@code %PDF-}). */
    PDF;

    /** The kind of these bytes, or empty for anything else. */
    public static Optional<ContentKind> detect(byte[] bytes) {
        if (startsWith(bytes, 0xFF, 0xD8, 0xFF)) {
            return Optional.of(JPEG);
        }
        if (startsWith(bytes, 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A)) {
            return Optional.of(PNG);
        }
        if (startsWith(bytes, '%', 'P', 'D', 'F', '-')) {
            return Optional.of(PDF);
        }
        return Optional.empty();
    }

    /** Whether the kind is an image, re-encoded before storage. */
    public boolean image() {
        return this != PDF;
    }

    /** Whether the bytes start with this signature. */
    private static boolean startsWith(byte[] bytes, int... signature) {
        if (bytes.length < signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if ((bytes[i] & 0xFF) != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
