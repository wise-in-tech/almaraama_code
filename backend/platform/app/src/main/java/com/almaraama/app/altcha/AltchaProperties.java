package com.almaraama.app.altcha;

import java.time.Duration;
import java.util.List;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the anti-robot proof of work ({@code almaraama.altcha.*}, ADR-032).
 *
 * @param hmacKeyFile        secret file holding the HMAC key that signs the challenges, never in the image
 * @param allowEphemeralKey  development and tests only: generate a key at start-up when no file is given
 * @param maxNumber          difficulty: the widget searches a number up to this value (about one second at 100,000)
 * @param challengeTtl       validity of a challenge
 * @param protectedPaths     endpoints (without the {@code /api} context path) that require a solved challenge, all
 *                           in this one place
 * @param verificationEnabled tests only: {@code false} skips the check; refused at start-up outside profiles dev/test
 */
@ConfigurationProperties("almaraama.altcha")
public record AltchaProperties(
        String hmacKeyFile,
        @DefaultValue("false") boolean allowEphemeralKey,
        @DefaultValue("100000") long maxNumber,
        @DefaultValue("10m") Duration challengeTtl,
        @DefaultValue({"/v1/auth/password-reset", "/v1/accounts/verify-email/resend", "/v1/registrations"})
        List<String> protectedPaths,
        @DefaultValue("true") boolean verificationEnabled) {
}
