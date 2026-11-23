package com.almaraama.identity.application;

import com.almaraama.identity.api.events.AccountEmailKind;
import java.time.Duration;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;

/**
 * Settings of the identity module ({@code almaraama.identity.*}).
 *
 * @param accessTokenTtl         lifetime of an access token; short, because it cannot be revoked
 * @param refreshTokenTtl        lifetime of a refresh token; each use replaces it
 * @param invitationTtl          validity of a "set my password" link (D4, spec starting value 7 days)
 * @param emailVerificationTtl   validity of an e-mail verification link (D1, ADR-030)
 * @param passwordResetTtl       validity of a password reset link (D5, ADR-030)
 * @param emailsPerHour          account e-mails of one kind sent at most to one person per hour (ADR-031)
 * @param linkBaseUrl            public address of the web application in the links; never taken from the request
 * @param defaultPhoneRegion     country (ISO 3166) assumed for a number typed in national form, such as {@code 0661…}
 * @param jwt                    signing key of access tokens
 */
@ConfigurationProperties("almaraama.identity")
public record IdentityProperties(
        @DefaultValue("10m") Duration accessTokenTtl,
        @DefaultValue("30d") Duration refreshTokenTtl,
        @DefaultValue("7d") Duration invitationTtl,
        @DefaultValue("48h") Duration emailVerificationTtl,
        @DefaultValue("1h") Duration passwordResetTtl,
        @DefaultValue("3") int emailsPerHour,
        @DefaultValue("http://localhost:5173") String linkBaseUrl,
        @DefaultValue("DZ") String defaultPhoneRegion,
        @DefaultValue Jwt jwt) {

    /** Validity of the link carried by an e-mail of this kind; {@code ZERO} for an e-mail without link. */
    public Duration linkTtl(AccountEmailKind kind) {
        return switch (kind) {
            case SET_PASSWORD -> invitationTtl;
            case EMAIL_VERIFICATION -> emailVerificationTtl;
            case PASSWORD_RESET -> passwordResetTtl;
            case PASSWORD_CHANGED, ALREADY_REGISTERED -> Duration.ZERO;
        };
    }

    /**
     * @param issuer              {@code iss} claim of the access tokens
     * @param privateKeyLocation  file holding the RSA private key (PKCS#8 PEM), mounted as a secret, never in the image
     * @param allowEphemeralKey   development and tests only: generate a key at start-up when no file is given
     */
    public record Jwt(
            @DefaultValue("almaraama") String issuer,
            String privateKeyLocation,
            @DefaultValue("false") boolean allowEphemeralKey) {
    }
}
