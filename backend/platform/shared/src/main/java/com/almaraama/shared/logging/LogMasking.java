package com.almaraama.shared.logging;

/**
 * Masks personal data before it is written to a technical log (ADR-033, ASVS V7.1, law 18-07). Pure functions, no
 * dependency (CF-9). Names, comments, passwords, tokens and link secrets are never logged at all: only e-mails and
 * phone numbers may appear, masked by these functions.
 */
public final class LogMasking {

    private static final String HIDDEN = "***";

    /** Static helpers only. */
    private LogMasking() {
    }

    /** {@code amina@example.dz} becomes {@code ***@example.dz}; anything else becomes {@code ***}. */
    public static String email(String email) {
        if (email == null) {
            return HIDDEN;
        }
        int at = email.lastIndexOf('@');
        return at < 0 || at == email.length() - 1 ? HIDDEN : HIDDEN + email.substring(at);
    }

    /** {@code +213661234567} becomes {@code +213*******67}: the country prefix and the last two digits only. */
    public static String phone(String phone) {
        if (phone == null || phone.length() < 7) {
            return HIDDEN;
        }
        return phone.substring(0, 4) + "*".repeat(phone.length() - 6) + phone.substring(phone.length() - 2);
    }
}
