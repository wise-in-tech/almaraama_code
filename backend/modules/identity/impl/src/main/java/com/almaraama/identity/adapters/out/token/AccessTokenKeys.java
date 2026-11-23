package com.almaraama.identity.adapters.out.token;

import com.almaraama.identity.application.IdentityProperties;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.KeyPair;
import java.security.KeyPairGenerator;
import java.security.interfaces.RSAPrivateCrtKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.RSAPublicKeySpec;
import java.util.Base64;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * The RSA key pair that signs access tokens. In every real environment the private key is a file mounted as a
 * secret, outside the image and the repository (security baseline). Only development and tests may generate a
 * throwaway key at start-up; tokens then stop working at the next restart.
 */
final class AccessTokenKeys {

    private static final Logger LOG = LoggerFactory.getLogger(AccessTokenKeys.class);

    private final RSAPublicKey publicKey;
    private final RSAPrivateCrtKey privateKey;

    /**
     * @param publicKey verifies tokens
     * @param privateKey signs tokens
     */
    private AccessTokenKeys(RSAPublicKey publicKey, RSAPrivateCrtKey privateKey) {
        this.publicKey = publicKey;
        this.privateKey = privateKey;
    }

    /**
     * Reads the key from the configured file; generates a throwaway one only when explicitly allowed, and fails at
     * start-up otherwise.
     */
    static AccessTokenKeys load(IdentityProperties.Jwt settings) {
        String location = settings.privateKeyLocation();
        if (location != null && !location.isBlank()) {
            return fromPemFile(Path.of(location));
        }
        if (settings.allowEphemeralKey()) {
            LOG.warn("No access-token signing key configured: using a throwaway key (development and tests only)");
            return generated();
        }
        throw new IllegalStateException("almaraama.identity.jwt.private-key-location is required: "
                + "mount the RSA private key (PKCS#8 PEM) as a secret file");
    }

    /** The public key, used to verify tokens. */
    RSAPublicKey publicKey() {
        return publicKey;
    }

    /** The private key, used to sign tokens. */
    RSAPrivateCrtKey privateKey() {
        return privateKey;
    }

    /** Reads a PKCS#8 PEM private key and derives its public key. */
    private static AccessTokenKeys fromPemFile(Path path) {
        try {
            String pem = Files.readString(path, StandardCharsets.US_ASCII);
            String base64 = pem.replaceAll("-----(BEGIN|END) PRIVATE KEY-----", "").replaceAll("\\s", "");
            KeyFactory factory = KeyFactory.getInstance("RSA");
            RSAPrivateCrtKey privateKey = (RSAPrivateCrtKey) factory.generatePrivate(
                    new PKCS8EncodedKeySpec(Base64.getDecoder().decode(base64)));
            RSAPublicKey publicKey = (RSAPublicKey) factory.generatePublic(
                    new RSAPublicKeySpec(privateKey.getModulus(), privateKey.getPublicExponent()));
            return new AccessTokenKeys(publicKey, privateKey);
        } catch (IOException | GeneralSecurityException | IllegalArgumentException | ClassCastException e) {
            throw new IllegalStateException("Cannot read the access-token signing key (RSA, PKCS#8 PEM expected)", e);
        }
    }

    /** A new RSA 3072 key pair, lost at the next restart. */
    private static AccessTokenKeys generated() {
        try {
            KeyPairGenerator generator = KeyPairGenerator.getInstance("RSA");
            generator.initialize(3072);
            KeyPair pair = generator.generateKeyPair();
            return new AccessTokenKeys((RSAPublicKey) pair.getPublic(), (RSAPrivateCrtKey) pair.getPrivate());
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("RSA is always available", e);
        }
    }
}
