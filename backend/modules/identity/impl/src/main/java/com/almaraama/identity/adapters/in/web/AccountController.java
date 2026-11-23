package com.almaraama.identity.adapters.in.web;

import com.almaraama.identity.adapters.in.web.IdentityWebDtos.EmailRequest;
import com.almaraama.identity.adapters.in.web.IdentityWebDtos.LinkTokenRequest;
import com.almaraama.identity.application.EmailVerification;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * E-mail verification (D1, ADR-030). Public endpoints; the ALTCHA proof of {@code resend} is checked by the platform's
 * security layer (ADR-032).
 */
@RestController
@RequestMapping("/v1/accounts")
class AccountController {

    private final EmailVerification verification;

    /** @param verification e-mail verification use cases */
    AccountController(EmailVerification verification) {
        this.verification = verification;
    }

    /** {@code POST /v1/accounts/verify-email} (D1): verifies the e-mail; {@code LINK_INVALID_OR_EXPIRED} otherwise. */
    @PostMapping("/verify-email")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    void verify(@Valid @RequestBody LinkTokenRequest request) {
        verification.verify(request.token());
    }

    /** {@code POST /v1/accounts/verify-email/resend} (D1): always {@code 202}; a link leaves only when expected. */
    @PostMapping("/verify-email/resend")
    @ResponseStatus(HttpStatus.ACCEPTED)
    void resend(@Valid @RequestBody EmailRequest request) {
        verification.resend(request.email());
    }
}
