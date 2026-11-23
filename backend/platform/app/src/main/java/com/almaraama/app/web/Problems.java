package com.almaraama.app.web;

import com.almaraama.shared.error.ErrorCategory;
import java.net.URI;
import java.util.List;
import java.util.Map;
import org.springframework.http.HttpStatus;
import org.slf4j.MDC;
import org.springframework.http.ProblemDetail;

/** Builds the RFC 9457 problems of the contract ({@code Problem} in api/openapi.yaml). */
public final class Problems {

    /** Static helpers only. */
    private Problems() {
    }

    /**
     * A problem with the contract's stable {@code code}, the status's standard title and the request's {@code
     * requestId} (ADR-033).
     */
    public static ProblemDetail of(HttpStatus status, String code) {
        ProblemDetail problem = ProblemDetail.forStatus(status);
        problem.setType(URI.create("about:blank"));
        problem.setTitle(status.getReasonPhrase());
        problem.setProperty("code", code);
        problem.setProperty("requestId", requestId());
        return problem;
    }

    /** The identifier of the current request, or {@code unknown} outside a request. */
    private static String requestId() {
        String id = MDC.get(RequestIdFilter.MDC_KEY);
        return id == null ? "unknown" : id;
    }

    /** A validation problem listing each field in error with the name of the failed constraint. */
    public static ProblemDetail withViolations(HttpStatus status, String code, List<Map<String, String>> violations) {
        ProblemDetail problem = of(status, code);
        problem.setProperty("violations", violations);
        return problem;
    }

    /** HTTP status of each business error category ({@code NOT_FOUND} also covers "outside the perimeter", M3). */
    static HttpStatus status(ErrorCategory category) {
        return switch (category) {
            case VALIDATION -> HttpStatus.BAD_REQUEST;
            case UNAUTHENTICATED -> HttpStatus.UNAUTHORIZED;
            case PERMISSION_DENIED -> HttpStatus.FORBIDDEN;
            case NOT_FOUND -> HttpStatus.NOT_FOUND;
            case CONFLICT -> HttpStatus.CONFLICT;
            case RULE_VIOLATION -> HttpStatus.UNPROCESSABLE_CONTENT;
            case TOO_MANY_REQUESTS -> HttpStatus.TOO_MANY_REQUESTS;
            case UNSUPPORTED_CONTENT -> HttpStatus.UNSUPPORTED_MEDIA_TYPE;
            case TOO_LARGE -> HttpStatus.CONTENT_TOO_LARGE;
        };
    }

    /** The fixed JSON body for authentication failures written outside Spring MVC (security filters). */
    public static String json(HttpStatus status, String code) {
        return "{\"type\":\"about:blank\",\"title\":\"" + status.getReasonPhrase() + "\",\"status\":"
                + status.value() + ",\"code\":\"" + code + "\",\"requestId\":\"" + requestId() + "\"}";
    }
}
