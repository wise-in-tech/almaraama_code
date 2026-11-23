package com.almaraama.identity.application;

import java.time.Duration;
import java.time.Instant;

/**
 * A session just opened or renewed on a device. The refresh token goes into an HttpOnly cookie, the access token
 * into the response body.
 */
public record OpenedSession(String accessToken, Duration accessTokenLifetime, String refreshToken,
                            Instant refreshTokenExpiresAt) {

    /** Never prints the tokens, even by accident in a log. */
    @Override
    public String toString() {
        return "OpenedSession[tokens hidden]";
    }
}
