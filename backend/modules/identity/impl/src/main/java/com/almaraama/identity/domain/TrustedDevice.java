package com.almaraama.identity.domain;

import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.UuidV7;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import java.time.Instant;
import java.util.UUID;

/**
 * A device on which a person is signed in (D4). Its refresh tokens are bound to it; ending the session revokes the
 * device, so none of its tokens works any more.
 */
@Entity
@Table(schema = "identity", name = "trusted_device")
public class TrustedDevice {

    private static final int MAX_LABEL_LENGTH = 120;

    @Id
    private UUID id;

    @Column(name = "person_id", nullable = false)
    private UUID personId;

    @Column(name = "label")
    private String label;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "last_seen_at", nullable = false)
    private Instant lastSeenAt;

    @Column(name = "revoked_at")
    private Instant revokedAt;

    @Version
    private Long version;

    /** For JPA only. */
    protected TrustedDevice() {
    }

    /** A device the person just signed in from, labelled from its name or browser. */
    public static TrustedDevice register(PersonId personId, String label, Instant now) {
        TrustedDevice device = new TrustedDevice();
        device.id = UuidV7.next();
        device.personId = personId.value();
        device.label = label == null ? null : label.strip().substring(0, Math.min(label.strip().length(), MAX_LABEL_LENGTH));
        device.createdAt = now;
        device.lastSeenAt = now;
        return device;
    }

    /** Records the device's last activity. */
    public void seen(Instant now) {
        lastSeenAt = now;
    }

    /** Ends the session on this device (sign-out or theft detection). */
    public void revoke(Instant now) {
        if (revokedAt == null) {
            revokedAt = now;
        }
    }

    /** Whether the session on this device was ended. */
    public boolean isRevoked() {
        return revokedAt != null;
    }

    /** Identifier of the device. */
    public UUID id() {
        return id;
    }

    /** The person who owns the device. */
    public PersonId personId() {
        return new PersonId(personId);
    }
}
