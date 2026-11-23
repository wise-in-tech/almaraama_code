package com.almaraama.sites.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;

/** Checks of free text entered by people: trimmed, required or optional, bounded. */
final class Texts {

    /** Static helpers only. */
    private Texts() {
    }

    /** The trimmed text; the given {@code VALIDATION} error code when empty or too long. */
    static String required(String value, int maxLength, String errorCode) {
        String stripped = value == null ? "" : value.strip();
        if (stripped.isEmpty() || stripped.length() > maxLength) {
            throw new BusinessException(errorCode, ErrorCategory.VALIDATION, "Required, " + maxLength + " max");
        }
        return stripped;
    }

    /** The trimmed text, {@code null} when blank; the error code when too long. */
    static String optional(String value, int maxLength, String errorCode) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return required(value, maxLength, errorCode);
    }
}
