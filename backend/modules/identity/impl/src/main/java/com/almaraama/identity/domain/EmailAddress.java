package com.almaraama.identity.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import java.util.Locale;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * An e-mail address, the sign-in identifier (D1, ADR-030). Always trimmed and in lower case, so that the same mailbox
 * can never hold two accounts. The format check is deliberately simple: the verification link is the real proof.
 *
 * @param value the normalized address
 */
public record EmailAddress(String value) {

    private static final int MAX_LENGTH = 254;
    private static final Pattern SHAPE = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]+\\.[^@\\s]+$");

    /** Rejects a value that is not normalized; use {@link #parse} for typed input. */
    public EmailAddress {
        if (value == null || !value.equals(normalize(value)) || !SHAPE.matcher(value).matches()
                || value.length() > MAX_LENGTH) {
            throw invalid();
        }
    }

    /** Reads a typed address: trims and lower-cases it; {@code EMAIL_INVALID} when it is not an address. */
    public static EmailAddress parse(String raw) {
        if (raw == null) {
            throw invalid();
        }
        return new EmailAddress(normalize(raw));
    }

    /** Like {@link #parse}, without an exception: for sign-in, where a malformed e-mail is just a wrong one. */
    public static Optional<EmailAddress> tryParse(String raw) {
        try {
            return Optional.of(parse(raw));
        } catch (BusinessException e) {
            return Optional.empty();
        }
    }

    /** The part before {@code @}, which a password must not be equal to (ADR-030). */
    public String localPart() {
        return value.substring(0, value.indexOf('@'));
    }

    /** The part after {@code @}: the only part of an address ever written to a technical log (ADR-033). */
    public String domain() {
        return value.substring(value.indexOf('@') + 1);
    }

    /** Trimmed and lower case, the stored form. */
    private static String normalize(String raw) {
        return raw.strip().toLowerCase(Locale.ROOT);
    }

    /** The single answer for an unusable address. */
    private static BusinessException invalid() {
        return new BusinessException("EMAIL_INVALID", ErrorCategory.VALIDATION, "Not an e-mail address");
    }
}
