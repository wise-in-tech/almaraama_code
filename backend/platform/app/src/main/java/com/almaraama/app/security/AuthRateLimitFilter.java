package com.almaraama.app.security;

import com.almaraama.app.web.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.time.Clock;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Limits the number of sign-in and invitation requests per client address and per minute (security baseline).
 * Second barrier: the reverse proxy limits them too. The client address comes from {@code X-Forwarded-For}, set by
 * the proxy; the application port is never reachable without it.
 */
class AuthRateLimitFilter extends OncePerRequestFilter {

    private static final int MAX_TRACKED_CLIENTS = 50_000;

    /** Requests counted for one client address during one minute. */
    private record Window(long minute, int count) {
    }

    private final int requestsPerMinute;
    private final Clock clock;
    private final Map<String, Window> windows = new ConcurrentHashMap<>();

    /**
     * @param requestsPerMinute maximum requests per client address and per minute on the public sign-in, invitation and
     * registration endpoints
     * @param clock source of time, replaceable in tests
     */
    AuthRateLimitFilter(int requestsPerMinute, Clock clock) {
        this.requestsPerMinute = requestsPerMinute;
        this.clock = clock;
    }

    /**
     * Counts the request for its client address and answers {@code 429 RATE_LIMITED} with {@code Retry-After} when the
     * limit of the current minute is reached; otherwise passes the request on.
     */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        long minute = clock.millis() / 60_000;
        if (windows.size() > MAX_TRACKED_CLIENTS) {
            windows.values().removeIf(window -> window.minute() < minute);
        }
        Window window = windows.merge(request.getRemoteAddr(), new Window(minute, 1),
                (previous, fresh) -> previous.minute() == minute
                        ? new Window(minute, previous.count() + 1)
                        : fresh);
        if (window.count() > requestsPerMinute) {
            long retryAfter = 60 - (clock.millis() / 1000) % 60;
            response.setStatus(HttpStatus.TOO_MANY_REQUESTS.value());
            response.setHeader(HttpHeaders.RETRY_AFTER, String.valueOf(retryAfter));
            response.setContentType("application/problem+json");
            response.getWriter().write(Problems.json(HttpStatus.TOO_MANY_REQUESTS, "RATE_LIMITED"));
            return;
        }
        chain.doFilter(request, response);
    }
}
