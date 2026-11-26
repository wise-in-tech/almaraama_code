package com.almaraama.maintenance.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.i18n.LocalizedText;
import java.util.regex.Pattern;

/** Checks shared by the reference data of the module. */
final class Checks {

    private Checks() {
    }

    /** A readable code of at most {@code max} characters, else {@code errorCode}. */
    static String code(String code, int max, String errorCode) {
        if (code == null || code.length() > max || !Pattern.matches("^[A-Z0-9][A-Z0-9_-]*$", code)) {
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
}
