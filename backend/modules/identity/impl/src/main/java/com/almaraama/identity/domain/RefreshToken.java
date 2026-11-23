package com.almaraama.identity.domain;

import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * A refresh token bound to a device, valid once (security baseline): each use replaces it with a new one. A token
 * presented after its use reveals a theft, and the device session is ended.
 */
@Entity
@Table(schema = "identity", name = "refresh_token")
public class RefreshToken {

    @Id
    private UUID id;

    @Column(name = "device_id", nullable = false)
    private UUID deviceId;

    @Column(name = "token_hash", nullable = false, unique = true)
    private String tokenHash;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    @Column(name = "used_at")
    private Instant usedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected RefreshToken() {
    }

    /** A new single-use refresh token for the device; only its hash is kept. */
    public static RefreshToken issue(UUID deviceId, String tokenHash, Instant now, Duration ttl) {
        RefreshToken token = new RefreshToken();
        token.id = UuidV7.next();
        token.deviceId = deviceId;
        token.tokenHash = tokenHash;
        token.issuedAt = now;
        token.expiresAt = now.plus(ttl);
        return token;
    }

    /** Whether it was already exchanged; presenting it again signals a theft. */
    public boolean isUsed() {
        return usedAt != null;
    }

    /** Whether its lifetime is over. */
    public boolean isExpired(Instant now) {
        return !now.isBefore(expiresAt);
    }

    /** Records its single use. */
    public void markUsed(Instant now) {
        usedAt = now;
    }

    /** The device it belongs to. */
    public UUID deviceId() {
        return deviceId;
    }

    /** End of validity, also the cookie's lifetime. */
    public Instant expiresAt() {
        return expiresAt;
    }
}
