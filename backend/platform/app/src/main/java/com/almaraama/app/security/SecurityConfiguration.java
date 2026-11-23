package com.almaraama.app.security;

import com.almaraama.app.web.Problems;
import com.almaraama.app.web.RequestIdFilter;
import java.time.Clock;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;
import org.springframework.http.HttpMethod;
import org.springframework.http.HttpStatus;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.AuthenticationEntryPoint;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.access.AccessDeniedHandler;
import org.springframework.security.web.header.writers.ReferrerPolicyHeaderWriter.ReferrerPolicy;

/**
 * Web security (security baseline, OWASP ASVS L2, ADR-030, ADR-032).
 * <ul>
 *   <li>Every request needs a valid access token (JWT, RS256), except sign-in, renewal, sign-out, forgotten password,
 *   "set my password" links, e-mail verification, ALTCHA challenges and join-code checks.
 *   The token is verified by the decoder of the identity module.</li>
 *   <li>Stateless: no HTTP session. The refresh cookie is read only by two endpoints that also require the
 *   {@code X-Almaraama-Csrf} header, so Spring's CSRF token is not needed.</li>
 *   <li>Authentication failures answer with a problem, never with a login page or a stack trace.</li>
 *   <li>Strict security headers: CSP {@code default-src 'none'} (the API serves no HTML), no framing, HSTS,
 *   no referrer.</li>
 * </ul>
 */
@Configuration(proxyBeanMethods = false)
class SecurityConfiguration {

    /**
     * The API security chain: public endpoints listed explicitly, a valid access token for everything else, stateless
     * sessions and strict security headers.
     */
    @Bean
    SecurityFilterChain apiSecurityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(csrf -> csrf.disable())
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .formLogin(form -> form.disable())
                .httpBasic(basic -> basic.disable())
                .logout(logout -> logout.disable())
                .requestCache(cache -> cache.disable())
                .authorizeHttpRequests(auth -> auth
                        .requestMatchers("/actuator/health", "/actuator/health/**", "/actuator/info",
                                "/actuator/prometheus").permitAll()
                        .requestMatchers("/v3/api-docs", "/v3/api-docs/**").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/auth/login", "/v1/auth/refresh", "/v1/auth/logout",
                                "/v1/auth/password-reset", "/v1/auth/password-reset/confirm").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/invitations/inspect", "/v1/invitations/accept")
                                .permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/accounts/verify-email",
                                "/v1/accounts/verify-email/resend").permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/altcha/challenge").permitAll()
                        .requestMatchers(HttpMethod.POST, "/v1/registration-qr/inspect", "/v1/registrations")
                                .permitAll()
                        .requestMatchers(HttpMethod.GET, "/v1/texts/*").permitAll()
                        .requestMatchers("/error").permitAll()
                        .anyRequest().authenticated())
                .oauth2ResourceServer(resource -> resource
                        .jwt(jwt -> { })
                        .authenticationEntryPoint(unauthenticated())
                        .accessDeniedHandler(denied()))
                .exceptionHandling(errors -> errors
                        .authenticationEntryPoint(unauthenticated())
                        .accessDeniedHandler(denied()))
                .headers(headers -> headers
                        .contentSecurityPolicy(csp -> csp.policyDirectives(
                                "default-src 'none'; frame-ancestors 'none'; base-uri 'none'; form-action 'none'"))
                        .frameOptions(frame -> frame.deny())
                        .httpStrictTransportSecurity(hsts -> hsts
                                .includeSubDomains(true)
                                .maxAgeInSeconds(31_536_000))
                        .referrerPolicy(referrer -> referrer.policy(ReferrerPolicy.NO_REFERRER)));
        return http.build();
    }

    /**
     * Registers the rate limiter on the public endpoints that can be abused (sign-in, forgotten password, account
     * links, ALTCHA challenges, registration by QR code, ADR-034), in addition to the proxy's own limits.
     */
    @Bean
    FilterRegistrationBean<AuthRateLimitFilter> authRateLimitFilter(
            @Value("${almaraama.security.auth-requests-per-minute:20}") int requestsPerMinute, Clock clock) {
        FilterRegistrationBean<AuthRateLimitFilter> registration =
                new FilterRegistrationBean<>(new AuthRateLimitFilter(requestsPerMinute, clock));
        registration.addUrlPatterns("/v1/auth/*", "/v1/invitations/*", "/v1/accounts/*", "/v1/altcha/*",
                "/v1/registrations", "/v1/registration-qr/inspect");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 10);
        return registration;
    }

    /**
     * Rate limit of the public text bundles (ADR-029): 60 requests per minute and address, enough for the background
     * revalidation of every screen of a household behind one address.
     */
    @Bean
    FilterRegistrationBean<AuthRateLimitFilter> textsRateLimitFilter(
            @Value("${almaraama.security.texts-requests-per-minute:60}") int requestsPerMinute, Clock clock) {
        FilterRegistrationBean<AuthRateLimitFilter> registration =
                new FilterRegistrationBean<>(new AuthRateLimitFilter(requestsPerMinute, clock));
        registration.addUrlPatterns("/v1/texts/*");
        registration.setName("textsRateLimitFilter");
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 11);
        return registration;
    }

    /**
     * Gives every request its {@code requestId} before anything else runs, so that every log line and every problem
     * carries it (ADR-033).
     */
    @Bean
    FilterRegistrationBean<RequestIdFilter> requestIdFilter() {
        FilterRegistrationBean<RequestIdFilter> registration = new FilterRegistrationBean<>(new RequestIdFilter());
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE);
        return registration;
    }

    /**
     * Answers a missing or invalid access token with a {@code 401 UNAUTHENTICATED} problem, never with a login page.
     */
    private static AuthenticationEntryPoint unauthenticated() {
        return (request, response, exception) -> {
            response.setStatus(HttpStatus.UNAUTHORIZED.value());
            response.setHeader("WWW-Authenticate", "Bearer");
            response.setContentType("application/problem+json");
            response.getWriter().write(Problems.json(HttpStatus.UNAUTHORIZED, "UNAUTHENTICATED"));
        };
    }

    /** Something outside the caller's rights is reported as not found, never as forbidden (M3). */
    private static AccessDeniedHandler denied() {
        return (request, response, exception) -> {
            response.setStatus(HttpStatus.NOT_FOUND.value());
            response.setContentType("application/problem+json");
            response.getWriter().write(Problems.json(HttpStatus.NOT_FOUND, "NOT_FOUND"));
        };
    }
}
