package com.almaraama.shared.error;

/**
 * Category of a business error. The web layer maps it to an HTTP status; the domain never knows about HTTP.
 * Something outside the caller's perimeter is {@link #NOT_FOUND}, so its existence is never revealed (M3).
 * {@link #PERMISSION_DENIED} is only for something the caller can already see but may not act on (ADR-026).
 */
public enum ErrorCategory {
    VALIDATION,
    UNAUTHENTICATED,
    PERMISSION_DENIED,
    NOT_FOUND,
    CONFLICT,
    RULE_VIOLATION,
    TOO_MANY_REQUESTS,
    /** A file whose content is not the expected kind (an import that is not an {@code .xlsx}, ADR-027). */
    UNSUPPORTED_CONTENT,
    /** A file or a request over its size or row limit (ADR-027). */
    TOO_LARGE
}
