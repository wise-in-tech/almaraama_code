package com.almaraama.identity.application;

import com.almaraama.identity.domain.AccessTokenIssuer;
import com.almaraama.identity.domain.DeviceSessionRepository;
import com.almaraama.identity.domain.RefreshToken;
import com.almaraama.identity.domain.SecretTokens;
import com.almaraama.identity.domain.TrustedDevice;
import com.almaraama.shared.id.PersonId;
import java.time.Instant;
import org.springframework.stereotype.Component;

/** Opens a session on a new device, or renews one on a known device. Runs inside the caller's transaction. */
@Component
class SessionOpener {

    private final DeviceSessionRepository sessions;
    private final AccessTokenIssuer accessTokens;
    private final IdentityProperties properties;

    /**
     * @param sessions devices and refresh tokens
     * @param accessTokens signs access tokens
     * @param properties token lifetimes
     */
    SessionOpener(DeviceSessionRepository sessions, AccessTokenIssuer accessTokens, IdentityProperties properties) {
        this.sessions = sessions;
        this.accessTokens = accessTokens;
        this.properties = properties;
    }

    /** Records the device the person signs in from. */
    TrustedDevice registerDevice(PersonId personId, String deviceName, Instant now) {
        return sessions.save(TrustedDevice.register(personId, deviceName, now));
    }

    /**
     * Ends the session on every device of the person, after a password reset (ASVS V3.3.3): their refresh tokens stop
     * working at once, their access tokens within their 10-minute lifetime. Returns the number of devices signed out.
     */
    int endAllSessions(PersonId personId, Instant now) {
        var devices = sessions.findActiveDevices(personId);
        devices.forEach(device -> {
            device.revoke(now);
            sessions.save(device);
        });
        return devices.size();
    }

    /**
     * Issues an access token and a single-use refresh token bound to the device; only the refresh token's hash is
     * stored.
     */
    OpenedSession issueTokens(TrustedDevice device, Instant now) {
        String refresh = SecretTokens.generate();
        RefreshToken token = sessions.save(
                RefreshToken.issue(device.id(), SecretTokens.hash(refresh), now, properties.refreshTokenTtl()));
        AccessTokenIssuer.AccessToken access = accessTokens.issue(device.personId(), device.id());
        return new OpenedSession(access.value(), access.lifetime(), refresh, token.expiresAt());
    }
}
