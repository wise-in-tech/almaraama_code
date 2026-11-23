package com.almaraama.identity.domain;

import com.almaraama.shared.id.PersonId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Out port: trusted devices and their refresh tokens. */
public interface DeviceSessionRepository {

    /** Loads a device. */
    Optional<TrustedDevice> findDevice(UUID deviceId);

    /** The devices of a person whose session is not ended, to end them all after a password reset (ASVS V3.3.3). */
    List<TrustedDevice> findActiveDevices(PersonId personId);

    /** Stores a device. */
    TrustedDevice save(TrustedDevice device);

    /** A refresh token by the hash of its secret, used or not (reuse detection). */
    Optional<RefreshToken> findRefreshToken(String tokenHash);

    /** Stores a refresh token. */
    RefreshToken save(RefreshToken token);
}
