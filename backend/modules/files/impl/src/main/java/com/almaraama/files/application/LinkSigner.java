package com.almaraama.files.application;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.UUID;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Signs and checks the short-lived links of files (ADR-038): HMAC-SHA256 of the file identifier and the expiry, with a
 * key read from a secret file ({@code ALMARAAMA_FILE_LINK_KEY_FILE}). A throwaway key is allowed only in profile
 * {@code dev} and in tests; links then stop working at each restart. Signatures are never logged (ADR-033).
 */
@Component
public class LinkSigner {

    private static final Logger LOG = LoggerFactory.getLogger(LinkSigner.class);

    private final byte[] key;
    private final Duration ttl;

    /**
     * @param keyFile           secret file holding at least 32 bytes of key material
     * @param allowEphemeralKey whether a random key may be used when no file is given (dev, tests)
     * @param ttl               validity of a link (5 minutes)
     */
    LinkSigner(@Value("${almaraama.files.link-key-file:}") String keyFile,
               @Value("${almaraama.files.allow-ephemeral-key:false}") boolean allowEphemeralKey,
               @Value("${almaraama.files.link-ttl:5m}") Duration ttl) {
        this.ttl = ttl;
        if (keyFile != null && !keyFile.isBlank()) {
            try {
                this.key = Files.readAllBytes(Path.of(keyFile));
            } catch (IOException e) {
                throw new UncheckedIOException("File link key unreadable", e);
            }
            if (key.length < 32) {
                throw new IllegalStateException("File link key shorter than 32 bytes");
            }
        } else if (allowEphemeralKey) {
            this.key = new byte[32];
            new SecureRandom().nextBytes(key);
            LOG.warn("File links signed with a throwaway key: they stop working at restart (dev and tests only)");
        } else {
            throw new IllegalStateException("almaraama.files.link-key-file is required (ADR-038)");
        }
    }

    /** End of validity of a link made now. */
    Instant expiryFrom(Instant now) {
        return now.plus(ttl);
    }

    /** The signature of a link to this file until this expiry, base64url without padding (43 characters). */
    String sign(UUID fileId, long expiresEpochSecond) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            byte[] digest = mac.doFinal((fileId + "." + expiresEpochSecond).getBytes(StandardCharsets.US_ASCII));
            return Base64.getUrlEncoder().withoutPadding().encodeToString(digest);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HMAC-SHA256 unavailable", e);
        }
    }

    /** Whether the signature is right (compared in constant time) and the link not expired. */
    boolean valid(UUID fileId, long expiresEpochSecond, String signature, Instant now) {
        if (signature == null || now.getEpochSecond() > expiresEpochSecond) {
            return false;
        }
        byte[] expected = sign(fileId, expiresEpochSecond).getBytes(StandardCharsets.US_ASCII);
        return MessageDigest.isEqual(expected, signature.getBytes(StandardCharsets.US_ASCII));
    }
}
