package com.almaraama.identity.adapters.in.web;

import com.almaraama.identity.application.OpenedSession;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.time.Clock;
import java.time.Duration;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;

/**
 * The refresh cookie: HttpOnly (invisible to scripts), Secure, SameSite=Strict, and sent only to
 * {@code /api/v1/auth}. The endpoints that read it also require the {@code X-Almaraama-Csrf} header, which a
 * cross-site page cannot send without a CORS preflight that the API never allows.
 */
@Component
class SessionCookies {

    static final String COOKIE_NAME = "almaraama_refresh";
    static final String CSRF_HEADER = "X-Almaraama-Csrf";
    private static final String COOKIE_PATH = "/api/v1/auth";

    private final Clock clock;

    /** @param clock computes the cookie's remaining lifetime */
    SessionCookies(Clock clock) {
        this.clock = clock;
    }

    /** Adds the refresh cookie of this session to the response. */
    void write(HttpServletResponse response, OpenedSession session) {
        response.addHeader(HttpHeaders.SET_COOKIE, headerValue(session));
    }

    /** The complete {@code Set-Cookie} value for this session's refresh token. */
    String headerValue(OpenedSession session) {
        Duration maxAge = Duration.between(clock.instant(), session.refreshTokenExpiresAt());
        return cookie(session.refreshToken(), maxAge).toString();
    }

    /** Replaces the refresh cookie with an expired empty one (sign-out). */
    void clear(HttpServletResponse response) {
        response.addHeader(HttpHeaders.SET_COOKIE, cookie("", Duration.ZERO).toString());
    }

    /** The refresh token sent by the browser, or {@code null} when there is none. */
    String read(HttpServletRequest request) {
        if (request.getCookies() == null) {
            return null;
        }
        for (var cookie : request.getCookies()) {
            if (COOKIE_NAME.equals(cookie.getName())) {
                return cookie.getValue();
            }
        }
        return null;
    }

    /**
     * {@code CSRF_HEADER_MISSING} unless the request carries {@code X-Almaraama-Csrf: 1}, which a cross-site page
     * cannot send.
     */
    void requireCsrfHeader(HttpServletRequest request) {
        if (!"1".equals(request.getHeader(CSRF_HEADER))) {
            throw new BusinessException("CSRF_HEADER_MISSING", ErrorCategory.VALIDATION, "Missing CSRF header");
        }
    }

    /** The refresh cookie with its fixed security attributes (ADR-025). */
    private static ResponseCookie cookie(String value, Duration maxAge) {
        return ResponseCookie.from(COOKIE_NAME, value)
                .httpOnly(true)
                .secure(true)
                .sameSite("Strict")
                .path(COOKIE_PATH)
                .maxAge(maxAge)
                .build();
    }
}
