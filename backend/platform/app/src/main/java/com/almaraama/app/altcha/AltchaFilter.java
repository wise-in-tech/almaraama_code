package com.almaraama.app.altcha;

import com.almaraama.app.web.Problems;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.util.Set;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpStatus;
import org.springframework.web.filter.OncePerRequestFilter;

/**
 * Requires a solved ALTCHA challenge in the {@code X-Altcha} header on the protected public endpoints (ADR-032);
 * otherwise answers {@code 400 ALTCHA_INVALID} before the request reaches a module. The modules know nothing about it.
 */
class AltchaFilter extends OncePerRequestFilter {

    static final String HEADER = "X-Altcha";
    private static final Logger LOG = LoggerFactory.getLogger(AltchaFilter.class);

    private final AltchaService altcha;
    private final Set<String> protectedPaths;

    /**
     * @param altcha         verifies the proofs
     * @param protectedPaths paths (inside the {@code /api} context) that require a proof
     */
    AltchaFilter(AltchaService altcha, Set<String> protectedPaths) {
        this.altcha = altcha;
        this.protectedPaths = Set.copyOf(protectedPaths);
    }

    /** Only POST requests to a protected path are checked. */
    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String path = request.getRequestURI().substring(request.getContextPath().length());
        return !"POST".equals(request.getMethod()) || !protectedPaths.contains(path);
    }

    /** Lets the request through with a valid proof; answers {@code 400 ALTCHA_INVALID} otherwise. */
    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain chain)
            throws ServletException, IOException {
        if (altcha.verify(request.getHeader(HEADER))) {
            chain.doFilter(request, response);
            return;
        }
        LOG.warn("ALTCHA proof refused on {}", request.getRequestURI());
        response.setStatus(HttpStatus.BAD_REQUEST.value());
        response.setContentType("application/problem+json");
        response.getWriter().write(Problems.json(HttpStatus.BAD_REQUEST, "ALTCHA_INVALID"));
    }
}
