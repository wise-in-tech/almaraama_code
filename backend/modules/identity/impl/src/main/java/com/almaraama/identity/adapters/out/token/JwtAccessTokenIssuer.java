package com.almaraama.identity.adapters.out.token;

import com.almaraama.identity.application.IdentityProperties;
import com.almaraama.identity.domain.AccessTokenIssuer;
import com.almaraama.shared.actor.ActorType;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.UuidV7;
import java.time.Clock;
import java.time.Instant;
import java.util.UUID;
import org.springframework.security.oauth2.jose.jws.SignatureAlgorithm;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;

/**
 * Signs access tokens (RS256). Claims: {@code sub} the person, {@code act} the actor type, {@code did} the device.
 * The perimeter is not in the token: it is computed for every request (CF-11), so a removed access takes effect
 * at once instead of when the token expires.
 */
class JwtAccessTokenIssuer implements AccessTokenIssuer {

    static final String ACTOR_TYPE_CLAIM = "act";
    static final String DEVICE_CLAIM = "did";

    private final JwtEncoder encoder;
    private final IdentityProperties properties;
    private final Clock clock;

    /**
     * @param encoder signs the claims
     * @param properties issuer and lifetime of the tokens
     * @param clock time of issue
     */
    JwtAccessTokenIssuer(JwtEncoder encoder, IdentityProperties properties, Clock clock) {
        this.encoder = encoder;
        this.properties = properties;
        this.clock = clock;
    }

    /** A signed token with the person as subject, the actor type and the device; valid for the configured lifetime. */
    @Override
    public AccessToken issue(PersonId personId, UUID deviceId) {
        Instant now = clock.instant();
        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(properties.jwt().issuer())
                .subject(personId.toString())
                .issuedAt(now)
                .expiresAt(now.plus(properties.accessTokenTtl()))
                .id(UuidV7.next().toString())
                .claim(ACTOR_TYPE_CLAIM, ActorType.PERSON.name())
                .claim(DEVICE_CLAIM, deviceId.toString())
                .build();
        JwsHeader header = JwsHeader.with(SignatureAlgorithm.RS256).build();
        String value = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(value, properties.accessTokenTtl());
    }
}
