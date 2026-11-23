package com.almaraama.sites.application;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;

/** Optimistic lock checked against the version the person read (contract {@code Version}). */
final class Versions {

    /** Static helper only. */
    private Versions() {
    }

    /** {@code VERSION_CONFLICT} when the object changed since the client read it. */
    static void check(long expected, long actual) {
        if (expected != actual) {
            throw new BusinessException("VERSION_CONFLICT", ErrorCategory.CONFLICT, "Changed in the meantime");
        }
    }
}
