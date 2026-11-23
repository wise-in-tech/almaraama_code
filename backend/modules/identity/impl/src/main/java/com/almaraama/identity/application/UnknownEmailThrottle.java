package com.almaraama.identity.application;

import com.almaraama.identity.domain.LockoutPolicy;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.stereotype.Component;

/**
 * Applies the same progressive lockout to e-mails that match no account (ADR-030), so the answers to repeated failed
 * sign-ins never reveal whether an account exists. Kept in memory: losing it on restart only resets counters (accepted
 * gap of ADR-030, single instance).
 */
@Component
class UnknownEmailThrottle {

    private static final int MAX_TRACKED = 10_000;
    private static final Duration FORGET_AFTER = Duration.ofHours(2);

    /** Failures counted for one unknown e-mail, with its lock. */
    private record Attempts(int failures, Instant lockedUntil, Instant lastFailure) {
    }

    private final Map<String, Attempts> attempts = new ConcurrentHashMap<>();

    /** When the unknown e-mail is locked, until when. */
    Optional<Instant> lockedUntil(String emailKey, Instant now) {
        Attempts current = attempts.get(emailKey);
        return current != null && current.lockedUntil() != null && now.isBefore(current.lockedUntil())
                ? Optional.of(current.lockedUntil())
                : Optional.empty();
    }

    /** Records a failure; returns the lock end when this failure starts a lockout. */
    Optional<Instant> recordFailure(String emailKey, Instant now) {
        if (attempts.size() >= MAX_TRACKED) {
            attempts.values().removeIf(a -> a.lastFailure().plus(FORGET_AFTER).isBefore(now));
        }
        Attempts updated = attempts.merge(emailKey, new Attempts(1, null, now), (previous, ignored) ->
                new Attempts(previous.failures() + 1, previous.lockedUntil(), now));
        Duration lock = LockoutPolicy.lockAfter(updated.failures());
        if (lock.isZero()) {
            return Optional.empty();
        }
        Instant until = now.plus(lock);
        attempts.put(emailKey, new Attempts(updated.failures(), until, now));
        return Optional.of(until);
    }
}
