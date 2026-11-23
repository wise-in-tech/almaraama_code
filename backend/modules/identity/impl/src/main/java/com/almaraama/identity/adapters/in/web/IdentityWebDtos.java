package com.almaraama.identity.adapters.in.web;

import com.almaraama.identity.api.AccountStatus;
import com.almaraama.shared.i18n.Language;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.UUID;

/**
 * Request and response bodies, as defined in api/openapi.yaml (v0.4.0, ADR-030). Validated before reaching a use case.
 * Records holding a password or a link secret never print it.
 */
final class IdentityWebDtos {

    /** Holder of the request and response records only. */
    private IdentityWebDtos() {
    }

    /** Body carrying the secret of an account link (after {@code #}); never put in a URL path. */
    record LinkTokenRequest(@NotBlank @Size(min = 20, max = 100) String token) {

        /** Never prints the secret, even by accident in a log. */
        @Override
        public String toString() {
            return "LinkTokenRequest[secret hidden]";
        }
    }

    /** What the "set my password" screen shows before the password is chosen. */
    record InvitationPreview(String firstName, String email, Language language) {
    }

    /** Body of {@code POST /v1/invitations/accept}: the link secret and the chosen password (D4). */
    record AcceptInvitationRequest(
            @NotBlank @Size(min = 20, max = 100) String token,
            @NotNull @Size(min = 1, max = 512) String password,
            @Size(max = 120) String deviceName) {

        /** Never prints the password or the secret, even by accident in a log. */
        @Override
        public String toString() {
            return "AcceptInvitationRequest[secrets hidden]";
        }
    }

    /**
     * Body of {@code POST /v1/auth/password-reset/confirm}: the reset link secret and the new password (D5). The length
     * rules give {@code PASSWORD_TOO_SHORT} or {@code PASSWORD_TOO_LONG} (422), so the bound here is only a safety
     * limit.
     */
    record ResetPasswordRequest(
            @NotBlank @Size(min = 20, max = 100) String token,
            @NotNull @Size(min = 1, max = 512) String password) {

        /** Never prints the password or the secret, even by accident in a log. */
        @Override
        public String toString() {
            return "ResetPasswordRequest[secrets hidden]";
        }
    }

    /** Body of the public requests that send an e-mail: forgotten password, new verification link. */
    record EmailRequest(@NotBlank @Email @Size(max = 254) String email) {

        /** Never prints the address (ADR-033). */
        @Override
        public String toString() {
            return "EmailRequest[address hidden]";
        }
    }

    /** Body of {@code POST /v1/auth/login}: e-mail and password (D5). */
    record LoginRequest(
            @NotBlank @Size(max = 254) String email,
            @NotNull @Size(min = 1, max = 128) String password,
            @Size(max = 120) String deviceName) {

        /** Never prints the password or the address, even by accident in a log. */
        @Override
        public String toString() {
            return "LoginRequest[secrets hidden]";
        }
    }

    /** Access token returned in the body; the refresh token travels only in the HttpOnly cookie. */
    record SessionResponse(String accessToken, String tokenType, long expiresIn) {
    }

    /** Optional fields are left out when empty, as the contract declares them (not {@code null}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MeResponse(UUID id, String firstName, String lastName, String email, Instant emailVerifiedAt, String phone,
                      Language language, AccountStatus status) {
    }
}
