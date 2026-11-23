package com.almaraama.app.altcha;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import org.altcha.altcha.v1.Altcha;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.env.Environment;
import org.springframework.core.env.Profiles;
import org.springframework.stereotype.Component;

/**
 * Creates and verifies ALTCHA challenges (ADR-032) with {@code org.altcha:altcha} (MIT), protocol v1, the format of
 * the widget. Challenges are signed with an HMAC key from a secret file, expire after {@code challengeTtl}, and a
 * solved challenge is accepted only once (signatures remembered in memory until they expire: accepted gap, single
 * instance).
 */
@Component
public class AltchaService {

    private static final Logger LOG = LoggerFactory.getLogger(AltchaService.class);
    private static final int MAX_REMEMBERED = 100_000;

    /** A challenge as the widget expects it. */
    public record Challenge(String algorithm, String challenge, long maxnumber, String salt, String signature) {
    }

    private final AltchaProperties properties;
    private final Clock clock;
    private final String hmacKey;
    private final Map<String, Instant> used = new ConcurrentHashMap<>();

    /**
     * Reads the HMAC key; a throwaway key is allowed only when {@code allowEphemeralKey} is set, and switching the
     * verification off is refused outside the dev and test profiles.
     */
    AltchaService(AltchaProperties properties, Clock clock, Environment environment) {
        this.properties = properties;
        this.clock = clock;
        if (!properties.verificationEnabled() && !environment.acceptsProfiles(Profiles.of("dev", "test"))) {
            throw new IllegalStateException("almaraama.altcha.verification-enabled=false outside dev and test");
        }
        this.hmacKey = loadKey(properties);
    }

    /** A new signed challenge, valid {@code challengeTtl}. */
    public Challenge newChallenge() {
        try {
            Altcha.ChallengeOptions options = new Altcha.ChallengeOptions()
                    .algorithm(Altcha.Algorithm.SHA256)
                    .maxNumber(properties.maxNumber())
                    .hmacKey(hmacKey)
                    .expires(clock.instant().plus(properties.challengeTtl()).getEpochSecond());
            Altcha.Challenge c = Altcha.createChallenge(options);
            return new Challenge("SHA-256", c.challenge(), c.maxnumber(), c.salt(), c.signature());
        } catch (Exception e) {
            throw new IllegalStateException("ALTCHA challenge not created", e);
        }
    }

    /**
     * Whether the base64 payload solves a challenge we signed, not expired and never used before; a valid payload is
     * remembered so it cannot be replayed.
     */
    public boolean verify(String payload) {
        if (!properties.verificationEnabled()) {
            return true;
        }
        if (payload == null || payload.isBlank() || payload.length() > 2000) {
            return false;
        }
        try {
            if (!Altcha.verifySolution(payload, hmacKey, true)) {
                return false;
            }
        } catch (Exception e) {
            LOG.debug("ALTCHA payload unreadable: {}", e.getClass().getSimpleName());
            return false;
        }
        Instant now = clock.instant();
        if (used.size() > MAX_REMEMBERED) {
            used.values().removeIf(expiry -> expiry.isBefore(now));
        }
        return used.putIfAbsent(payload, now.plus(properties.challengeTtl())) == null;
    }

    /** The key from the secret file, or a random one when allowed (development and tests). */
    private static String loadKey(AltchaProperties properties) {
        if (properties.hmacKeyFile() != null && !properties.hmacKeyFile().isBlank()) {
            try {
                String key = Files.readString(Path.of(properties.hmacKeyFile()), StandardCharsets.UTF_8).strip();
                if (key.length() < 32) {
                    throw new IllegalStateException("ALTCHA HMAC key shorter than 32 characters");
                }
                return key;
            } catch (IOException e) {
                throw new UncheckedIOException("ALTCHA HMAC key file not readable", e);
            }
        }
        if (!properties.allowEphemeralKey()) {
            throw new IllegalStateException("No ALTCHA HMAC key: set ALMARAAMA_ALTCHA_HMAC_KEY_FILE (ADR-032)");
        }
        LOG.warn("No ALTCHA HMAC key configured: using a throwaway key (development and tests only)");
        byte[] bytes = new byte[32];
        new SecureRandom().nextBytes(bytes);
        return HexFormat.of().formatHex(bytes);
    }

    /** Encodes a solved challenge as the widget does (tests and tools). */
    static String encode(String json) {
        return Base64.getEncoder().encodeToString(json.getBytes(StandardCharsets.UTF_8));
    }
}
