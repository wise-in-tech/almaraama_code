package com.almaraama.shared.error;

import java.time.Duration;
import java.util.Objects;
import java.util.Optional;

/**
 * Raised when a business rule is broken. Carries a stable English error code (for example {@code PIN_TOO_WEAK})
 * that the API returns in an RFC 9457 problem and the frontend translates. The message is for logs only: responses
 * never expose it raw, nor stack traces or internal IDs (security baseline).
 */
public class BusinessException extends RuntimeException {

    private final String code;
    private final ErrorCategory category;
    private final Duration retryAfter;

    /**
     * @param code stable English error code returned to the client, for example {@code UNIT_REFERENCE_TAKEN}
     * @param category gives the HTTP status
     * @param message for the server logs only, never sent to the client
     */
    public BusinessException(String code, ErrorCategory category, String message) {
        this(code, category, message, null);
    }

    /** Same, with the delay after which the client may try again (lockouts, rate limits). */
    public BusinessException(String code, ErrorCategory category, String message, Duration retryAfter) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.category = Objects.requireNonNull(category, "category");
        this.retryAfter = retryAfter;
    }

    /** The stable error code of the contract. */
    public String code() {
        return code;
    }

    /** The category, mapped to an HTTP status by the web layer. */
    public ErrorCategory category() {
        return category;
    }

    /** The delay before a new attempt, when the error is temporary. */
    public Optional<Duration> retryAfter() {
        return Optional.ofNullable(retryAfter);
    }
}
