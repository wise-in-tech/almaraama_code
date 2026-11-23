package com.almaraama.identity.domain;

import com.almaraama.shared.id.PersonId;
import java.time.Duration;
import java.util.UUID;

/** Out port: short-lived access tokens (JWT in production). */
public interface AccessTokenIssuer {

    /** A signed access token for the person on this device. */
    AccessToken issue(PersonId personId, UUID deviceId);

    /** A signed access token and how long it stays valid. */
    record AccessToken(String value, Duration lifetime) {
    }
}
