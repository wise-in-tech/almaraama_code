package com.almaraama.membership.adapters.in.web;

import com.almaraama.membership.adapters.in.web.MembershipWebDtos.RegistrationAccepted;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.RegistrationCodeRequest;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.RegistrationQrPreview;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.RegistrationQrResponse;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.RegistrationQrSettings;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.RegistrationRequest;
import com.almaraama.membership.application.RegistrationQrService;
import com.almaraama.membership.application.RegistrationService;
import com.almaraama.membership.domain.RegistrationQr;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.perimeter.Perimeter;
import jakarta.validation.Valid;
import java.util.UUID;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Registration by QR code (D2, D3, criteria 4 and 5, ADR-034): the promoteur's QR code of each site, the public
 * preview of a code and the public registration form (ALTCHA and rate limit in {@code platform/app}).
 */
@RestController
class RegistrationController {

    private final RegistrationQrService codes;
    private final RegistrationService registrations;
    private final String frontendUrl;

    /**
     * @param codes         the registration QR codes
     * @param registrations the registration flow
     * @param frontendUrl   public address of the application, put in the QR code (never the request's host)
     */
    RegistrationController(RegistrationQrService codes, RegistrationService registrations,
                           @Value("${almaraama.frontend-url:http://localhost:5173}") String frontendUrl) {
        this.codes = codes;
        this.registrations = registrations;
        this.frontendUrl = frontendUrl.endsWith("/") ? frontendUrl.substring(0, frontendUrl.length() - 1)
                : frontendUrl;
    }

    /** {@code GET /v1/sites/{id}/registration-qr}: needs {@code registration-qr:read}; created on first call. */
    @GetMapping("/v1/sites/{siteId}/registration-qr")
    ResponseEntity<RegistrationQrResponse> get(Perimeter perimeter, @PathVariable UUID siteId) {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore())
                .body(response(codes.get(perimeter, new SiteId(siteId))));
    }

    /** {@code PUT /v1/sites/{id}/registration-qr}: activates or deactivates it; needs {@code registration-qr:manage}. */
    @PutMapping("/v1/sites/{siteId}/registration-qr")
    RegistrationQrResponse setEnabled(Perimeter perimeter, @PathVariable UUID siteId,
                                      @Valid @RequestBody RegistrationQrSettings request) {
        return response(codes.setEnabled(perimeter, new SiteId(siteId), request.enabled(), request.version()));
    }

    /** {@code POST /v1/sites/{id}/registration-qr/rotate}: replaces it; needs {@code registration-qr:manage}. */
    @PostMapping("/v1/sites/{siteId}/registration-qr/rotate")
    RegistrationQrResponse rotate(Perimeter perimeter, @PathVariable UUID siteId) {
        return response(codes.rotate(perimeter, new SiteId(siteId)));
    }

    /** {@code POST /v1/registration-qr/inspect} (public): site and organization names; same 404 for every refusal. */
    @PostMapping("/v1/registration-qr/inspect")
    RegistrationQrPreview inspect(@Valid @RequestBody RegistrationCodeRequest request) {
        RegistrationQrService.Preview preview = codes.inspect(request.code());
        return new RegistrationQrPreview(preview.siteName(), preview.organizationName());
    }

    /**
     * {@code POST /v1/registrations} (public, ALTCHA): always {@code 202 CHECK_YOUR_EMAIL} once the code and the
     * fields are valid, whether the e-mail had an account or not (ADR-034).
     */
    @PostMapping("/v1/registrations")
    @ResponseStatus(HttpStatus.ACCEPTED)
    RegistrationAccepted register(@Valid @RequestBody RegistrationRequest request) {
        registrations.register(new RegistrationService.Registration(request.code(), request.firstName(),
                request.lastName(), request.email(), request.phone(), request.language(), request.comment(),
                request.password(), request.preRegistrationKey()));
        return new RegistrationAccepted("CHECK_YOUR_EMAIL");
    }

    /** Response body of a registration QR code, with the address the QR code holds (code after {@code #}). */
    private RegistrationQrResponse response(RegistrationQr qr) {
        return new RegistrationQrResponse(qr.siteId().value(), qr.display(), frontendUrl + "/r#" + qr.code(),
                qr.enabled(), qr.updatedAt(), qr.version());
    }
}
