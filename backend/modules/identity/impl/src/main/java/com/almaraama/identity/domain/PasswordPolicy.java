package com.almaraama.identity.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import java.text.Normalizer;
import java.util.Locale;

/**
 * Rules of a password chosen by a person (D5, ADR-030, ASVS V2.1): 8 to 128 characters counted as Unicode code points
 * after NFKC normalization, any printable character and spaces, no composition rule, never truncated. Refused when
 * among the most common passwords or equal to the e-mail or its part before {@code @}.
 */
public final class PasswordPolicy {

    public static final int MIN_LENGTH = 8;
    public static final int MAX_LENGTH = 128;

    private final CommonPasswordList commonPasswords;

    /** @param commonPasswords the embedded list of refused passwords */
    public PasswordPolicy(CommonPasswordList commonPasswords) {
        this.commonPasswords = commonPasswords;
    }

    /**
     * Checks a new password and returns it normalized (NFKC), the form that is hashed. Throws {@code
     * PASSWORD_TOO_SHORT}, {@code PASSWORD_TOO_LONG}, {@code PASSWORD_SAME_AS_EMAIL} or {@code PASSWORD_TOO_COMMON}
     * (all {@code RULE_VIOLATION}, 422 in the contract).
     */
    public String check(String raw, EmailAddress email) {
        String password = normalize(raw);
        int length = password.codePointCount(0, password.length());
        if (length < MIN_LENGTH) {
            throw rejected("PASSWORD_TOO_SHORT");
        }
        if (length > MAX_LENGTH) {
            throw rejected("PASSWORD_TOO_LONG");
        }
        String lower = password.toLowerCase(Locale.ROOT);
        if (lower.equals(email.value()) || lower.equals(email.localPart())) {
            throw rejected("PASSWORD_SAME_AS_EMAIL");
        }
        if (commonPasswords.contains(lower)) {
            throw rejected("PASSWORD_TOO_COMMON");
        }
        return password;
    }

    /**
     * The form of a typed password that is hashed and compared: NFKC, so that the same password typed on two keyboards
     * matches. A missing password becomes empty.
     */
    public static String normalize(String raw) {
        return raw == null ? "" : Normalizer.normalize(raw, Normalizer.Form.NFKC);
    }

    /** A password refused by one of the rules. */
    private static BusinessException rejected(String code) {
        return new BusinessException(code, ErrorCategory.RULE_VIOLATION, "Password refused by the policy");
    }
}
