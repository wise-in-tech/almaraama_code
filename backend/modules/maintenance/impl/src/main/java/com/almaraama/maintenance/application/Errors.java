package com.almaraama.maintenance.application;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;

/** Errors shared by the use cases of the module. */
final class Errors {

    private Errors() {
    }

    /** Outside the perimeter or unknown (M3). */
    static BusinessException notFound() {
        return new BusinessException("NOT_FOUND", ErrorCategory.NOT_FOUND, "Not found");
    }

    /** A rule of the module, answered with 422. */
    static BusinessException rule(String code) {
        return new BusinessException(code, ErrorCategory.RULE_VIOLATION, code);
    }

    /** {@code VERSION_CONFLICT} unless the client read the current version. */
    static void checkVersion(long expected, long actual) {
        if (expected != actual) {
            throw new BusinessException("VERSION_CONFLICT", ErrorCategory.CONFLICT, "Changed in the meantime");
        }
    }
}
