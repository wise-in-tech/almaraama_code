package com.almaraama.identity.adapters.in.web;

import com.almaraama.identity.adapters.in.web.IdentityWebDtos.EmailRequest;
import com.almaraama.identity.adapters.in.web.IdentityWebDtos.LoginRequest;
import com.almaraama.identity.adapters.in.web.IdentityWebDtos.ResetPasswordRequest;
import com.almaraama.identity.adapters.in.web.IdentityWebDtos.SessionResponse;
import com.almaraama.identity.application.Authentication;
import com.almaraama.identity.application.Authentication.LoginOutcome;
import com.almaraama.identity.application.Authentication.RenewalOutcome;
import com.almaraama.identity.application.OpenedSession;
import com.almaraama.identity.application.PasswordReset;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Sign-in, renewal, sign-out and forgotten password (D5, ADR-030). Outcomes become errors here, after the use case has
 * committed. The ALTCHA proof of {@code password-reset} is checked by the platform's security layer (ADR-032).
 */
@RestController
@RequestMapping("/v1/auth")
class AuthController {

    private final Authentication authentication;
    private final PasswordReset passwordReset;
    private final SessionCookies cookies;

    /**
     * @param authentication sign-in, renewal and sign-out use cases
     * @param passwordReset  forgotten-password use cases
     * @param cookies        writes and reads the refresh cookie
     */
    AuthController(Authentication authentication, PasswordReset passwordReset, SessionCookies cookies) {
        this.authentication = authentication;
        this.passwordReset = passwordReset;
        this.cookies = cookies;
    }

    /**
     * {@code POST /v1/auth/login} (D5): opens a session on this device; {@code AUTH_INVALID_CREDENTIALS} for a wrong
     * e-mail or password, {@code EMAIL_NOT_VERIFIED} after a correct password on an unverified account, {@code
     * AUTH_TEMPORARILY_LOCKED} after too many failures.
     */
    @PostMapping("/login")
    SessionResponse login(@Valid @RequestBody LoginRequest request,
                          @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                          HttpServletResponse response) {
        String device = request.deviceName() != null ? request.deviceName() : userAgent;
        return switch (authentication.login(request.email(), request.password(), device)) {
            case LoginOutcome.Success success -> open(response, success.session());
            case LoginOutcome.InvalidCredentials ignored -> throw new BusinessException(
                    "AUTH_INVALID_CREDENTIALS", ErrorCategory.UNAUTHENTICATED, "Wrong e-mail or password");
            case LoginOutcome.EmailNotVerified ignored -> throw new BusinessException(
                    "EMAIL_NOT_VERIFIED", ErrorCategory.PERMISSION_DENIED, "E-mail not verified yet");
            case LoginOutcome.Locked locked -> throw new BusinessException(
                    "AUTH_TEMPORARILY_LOCKED", ErrorCategory.TOO_MANY_REQUESTS, "Too many failures",
                    locked.retryAfter());
        };
    }

    /**
     * {@code POST /v1/auth/refresh}: replaces the refresh cookie and returns a new access token; requires the CSRF
     * header; {@code SESSION_INVALID} otherwise.
     */
    @PostMapping("/refresh")
    SessionResponse refresh(HttpServletRequest request, HttpServletResponse response) {
        cookies.requireCsrfHeader(request);
        return switch (authentication.renew(cookies.read(request))) {
            case RenewalOutcome.Renewed renewed -> open(response, renewed.session());
            case RenewalOutcome.Invalid ignored -> {
                cookies.clear(response);
                throw new BusinessException("SESSION_INVALID", ErrorCategory.UNAUTHENTICATED, "No valid session");
            }
        };
    }

    /**
     * {@code POST /v1/auth/logout}: ends the session of this device and clears the cookie; requires the CSRF header.
     */
    @PostMapping("/logout")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void logout(HttpServletRequest request, HttpServletResponse response) {
        cookies.requireCsrfHeader(request);
        authentication.logout(cookies.read(request));
        cookies.clear(response);
    }

    /**
     * {@code POST /v1/auth/password-reset} (D5): always {@code 202}, whatever the e-mail; a link leaves only for an
     * account that can use it. Protected by ALTCHA and the rate limit.
     */
    @PostMapping("/password-reset")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void requestPasswordReset(@Valid @RequestBody EmailRequest request) {
        passwordReset.request(request.email());
    }

    /**
     * {@code POST /v1/auth/password-reset/confirm} (D5): sets the new password and ends every session; {@code
     * LINK_INVALID_OR_EXPIRED} or {@code PASSWORD_*}.
     */
    @PostMapping("/password-reset/confirm")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void confirmPasswordReset(@Valid @RequestBody ResetPasswordRequest request) {
        passwordReset.confirm(request.token(), request.password());
    }

    /** Writes the refresh cookie and returns the access token in the body. */
    private SessionResponse open(HttpServletResponse response, OpenedSession session) {
        cookies.write(response, session);
        return new SessionResponse(session.accessToken(), "Bearer", session.accessTokenLifetime().toSeconds());
    }
}
