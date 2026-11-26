package com.almaraama.shared.error;

import java.time.Duration;
import java.util.List;
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
    private final List<String> fields;

    /**
     * @param code stable English error code returned to the client, for example {@code UNIT_REFERENCE_TAKEN}
     * @param category gives the HTTP status
     * @param message for the server logs only, never sent to the client
     */
    public BusinessException(String code, ErrorCategory category, String message) {
        this(code, category, message, (Duration) null);
    }

    /** Same, with the delay after which the client may try again (lockouts, rate limits). */
    public BusinessException(String code, ErrorCategory category, String message, Duration retryAfter) {
        this(code, category, message, retryAfter, List.of());
    }

    /**
     * Same, naming the fields at fault (for example the characteristics refused by {@code ATTRIBUTE_INVALID}); the web
     * layer returns them in {@code violations}.
     */
    public BusinessException(String code, ErrorCategory category, String message, List<String> fields) {
        this(code, category, message, null, fields);
    }

    /** Every detail at once. */
    private BusinessException(String code, ErrorCategory category, String message, Duration retryAfter,
                              List<String> fields) {
        super(message);
        this.code = Objects.requireNonNull(code, "code");
        this.category = Objects.requireNonNull(category, "category");
        this.retryAfter = retryAfter;
        this.fields = List.copyOf(fields);
    }

    /** The fields at fault, possibly empty. */
    public List<String> fields() {
        return fields;
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
