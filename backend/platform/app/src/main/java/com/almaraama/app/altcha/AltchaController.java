package com.almaraama.app.altcha;

import org.springframework.http.CacheControl;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/** {@code GET /v1/altcha/challenge}: a new anti-robot challenge for the public forms (ADR-032). */
@RestController
class AltchaController {

    private final AltchaService altcha;

    /** @param altcha creates the signed challenges */
    AltchaController(AltchaService altcha) {
        this.altcha = altcha;
    }

    /** A fresh challenge, never cached. Public and rate limited. */
    @GetMapping("/v1/altcha/challenge")
    ResponseEntity<AltchaService.Challenge> challenge() {
        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).body(altcha.newChallenge());
    }
}
