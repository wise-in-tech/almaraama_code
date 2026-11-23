package com.almaraama.identity.adapters.in.web;

import com.almaraama.identity.adapters.in.web.IdentityWebDtos.AcceptInvitationRequest;
import com.almaraama.identity.adapters.in.web.IdentityWebDtos.InvitationPreview;
import com.almaraama.identity.adapters.in.web.IdentityWebDtos.LinkTokenRequest;
import com.almaraama.identity.adapters.in.web.IdentityWebDtos.SessionResponse;
import com.almaraama.identity.application.InvitationAcceptance;
import com.almaraama.identity.application.OpenedSession;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/** "Set my password" links (D4, ADR-030): the secret travels in the body, never in a URL that proxies would log. */
@RestController
@RequestMapping("/v1/invitations")
class InvitationController {

    private final InvitationAcceptance acceptance;
    private final SessionCookies cookies;

    /**
     * @param acceptance invitation use cases
     * @param cookies writes the refresh cookie of the new session
     */
    InvitationController(InvitationAcceptance acceptance, SessionCookies cookies) {
        this.acceptance = acceptance;
        this.cookies = cookies;
    }

    /**
     * {@code POST /v1/invitations/inspect} (D4): greets the invited person; {@code LINK_INVALID_OR_EXPIRED} for any
     * unusable link.
     */
    @PostMapping("/inspect")
    InvitationPreview inspect(@Valid @RequestBody LinkTokenRequest request) {
        InvitationAcceptance.Preview preview = acceptance.inspect(request.token());
        return new InvitationPreview(preview.firstName(), preview.email(), preview.language());
    }

    /**
     * {@code POST /v1/invitations/accept} (D4, D5): sets the password, verifies the e-mail, activates the account and
     * signs in on this device; {@code PASSWORD_*} when the password is refused.
     */
    @PostMapping("/accept")
    SessionResponse accept(@Valid @RequestBody AcceptInvitationRequest request,
                           @RequestHeader(value = HttpHeaders.USER_AGENT, required = false) String userAgent,
                           HttpServletResponse response) {
        String device = request.deviceName() != null ? request.deviceName() : userAgent;
        OpenedSession session = acceptance.accept(request.token(), request.password(), device);
        cookies.write(response, session);
        return new SessionResponse(session.accessToken(), "Bearer", session.accessTokenLifetime().toSeconds());
    }
}
