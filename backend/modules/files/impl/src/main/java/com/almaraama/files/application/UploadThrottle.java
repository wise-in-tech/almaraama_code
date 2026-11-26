package com.almaraama.files.application;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import java.time.Duration;
import java.time.Instant;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Limits uploads per person and per minute (ADR-038, 30 by default), in memory: one application instance in P1; the
 * proxy limits requests too. A count is kept per person for the current minute only.
 */
@Component
class UploadThrottle {

    private record Window(long minute, int count) {
    }

    private final int perMinute;
    private final Map<UUID, Window> windows = new ConcurrentHashMap<>();

    /** @param perMinute uploads allowed per person and per minute */
    UploadThrottle(@Value("${almaraama.files.uploads-per-minute:30}") int perMinute) {
        this.perMinute = perMinute;
    }

    /** Counts one upload; {@code RATE_LIMITED} with a retry delay beyond the limit. */
    void acquire(UUID person, Instant now) {
        long minute = now.getEpochSecond() / 60;
        Window window = windows.compute(person, (id, current) -> current == null || current.minute() != minute
                ? new Window(minute, 1) : new Window(minute, current.count() + 1));
        if (window.count() > perMinute) {
            throw new BusinessException("RATE_LIMITED", ErrorCategory.TOO_MANY_REQUESTS, "Too many uploads",
                    Duration.ofSeconds(60 - now.getEpochSecond() % 60));
        }
        if (windows.size() > 10_000) {
            windows.entrySet().removeIf(e -> e.getValue().minute() != minute);
        }
    }
}
