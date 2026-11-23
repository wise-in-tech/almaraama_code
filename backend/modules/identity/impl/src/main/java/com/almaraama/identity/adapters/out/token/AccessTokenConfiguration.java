package com.almaraama.identity.adapters.out.token;

import com.almaraama.identity.application.IdentityProperties;
import com.almaraama.identity.domain.AccessTokenIssuer;
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import java.time.Clock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

/**
 * Signing and verification of access tokens. The decoder is used by the web security chain of the application to
 * authenticate every request; it checks the signature, the expiry and the issuer.
 */
@Configuration(proxyBeanMethods = false)
class AccessTokenConfiguration {

    /** The RSA key pair, from the secret file or, in development and tests only, generated at start-up. */
    @Bean
    AccessTokenKeys accessTokenKeys(IdentityProperties properties) {
        return AccessTokenKeys.load(properties.jwt());
    }

    /** Signs access tokens with the private key (RS256). */
    @Bean
    AccessTokenIssuer accessTokenIssuer(AccessTokenKeys keys, IdentityProperties properties, Clock clock) {
        RSAKey jwk = new RSAKey.Builder(keys.publicKey()).privateKey(keys.privateKey()).build();
        NimbusJwtEncoder encoder = new NimbusJwtEncoder(new ImmutableJWKSet<>(new JWKSet(jwk)));
        return new JwtAccessTokenIssuer(encoder, properties, clock);
    }

    /** Verifies the signature, issuer and expiry of every incoming access token. */
    @Bean
    JwtDecoder accessTokenDecoder(AccessTokenKeys keys, IdentityProperties properties) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withPublicKey(keys.publicKey()).build();
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                JwtValidators.createDefaultWithIssuer(properties.jwt().issuer())));
        return decoder;
    }
}
