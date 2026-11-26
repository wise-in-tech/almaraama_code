package com.almaraama.assets.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.i18n.LocalizedText;
import java.util.regex.Pattern;

/** Checks shared by the reference data of the module (codes, names, free texts). */
final class Labels {

    static final Pattern CODE = Pattern.compile("^[A-Z0-9][A-Z0-9_-]{0,29}$");

    private Labels() {
    }

    /** A readable code, else {@code errorCode} (validation). */
    static String code(String code, String errorCode) {
        if (code == null || !CODE.matcher(code).matches()) {
            throw new BusinessException(errorCode, ErrorCategory.VALIDATION, "Readable code");
        }
        return code;
    }

    /** A name in at least one language, 100 characters each, else {@code errorCode}. */
    static LocalizedText name(LocalizedText name, String errorCode) {
        if (name == null || !name.isPresent() || name.longerThan(100)) {
            throw new BusinessException(errorCode, ErrorCategory.VALIDATION, "A name is needed");
        }
        return name;
    }

    /** A mandatory trimmed text of at most {@code max} characters, else {@code errorCode}. */
    static String required(String text, int max, String errorCode) {
        String value = optional(text, max, errorCode);
        if (value == null) {
            throw new BusinessException(errorCode, ErrorCategory.VALIDATION, "Value required");
        }
        return value;
    }

    /** A trimmed text of at most {@code max} characters, {@code null} when blank, else {@code errorCode}. */
    static String optional(String text, int max, String errorCode) {
        if (text == null || text.isBlank()) {
            return null;
        }
        String value = text.strip();
        if (value.length() > max) {
            throw new BusinessException(errorCode, ErrorCategory.VALIDATION, "Too long");
        }
        return value;
    }
}
