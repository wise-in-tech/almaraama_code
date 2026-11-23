package com.almaraama.identity.domain;

import java.time.Duration;

/**
 * Progressive lockout after failed sign-ins (security baseline). The first four failures cost nothing; from the
 * fifth, sign-in is refused for a growing duration. A successful sign-in resets the count.
 */
public final class LockoutPolicy {

    public static final int FREE_ATTEMPTS = 4;

    /** Static rules only. */
    private LockoutPolicy() {
    }

    /** Lock duration after this many consecutive failures: none for the first four, then 1 min, 5 min, 15 min, 1 h. */
    public static Duration lockAfter(int consecutiveFailures) {
        return switch (consecutiveFailures) {
            case 0, 1, 2, 3, 4 -> Duration.ZERO;
            case 5 -> Duration.ofMinutes(1);
            case 6 -> Duration.ofMinutes(5);
            case 7 -> Duration.ofMinutes(15);
            default -> Duration.ofHours(1);
        };
    }
}
