package com.almaraama.shared.id;

import java.security.SecureRandom;
import java.time.Clock;
import java.util.UUID;
import java.util.concurrent.locks.ReentrantLock;

/**
 * Generates UUID version 7 identifiers in the application (CF-10), as defined by RFC 9562.
 * <p>
 * Layout: 48-bit Unix timestamp in milliseconds, then 74 random bits. IDs created in the same millisecond by one
 * generator stay ordered: the random part is incremented instead of redrawn, and the generator moves to the next
 * millisecond if it overflows. Time-ordered IDs keep B-tree indexes compact, and the random bits keep IDs
 * unguessable enough to be public (they are never the only access check: see M3).
 */
public final class UuidV7 {

    private static final UuidV7 DEFAULT = new UuidV7(Clock.systemUTC(), new SecureRandom());

    private static final long RAND_A_MASK = 0xFFFL;            // 12 bits
    private static final long RAND_B_MASK = 0x3FFF_FFFF_FFFF_FFFFL; // 62 bits

    private final Clock clock;
    private final SecureRandom random;
    private final ReentrantLock lock = new ReentrantLock();

    private long lastMillis = -1;
    private long randA;
    private long randB;

    /**
     * Package-private: tests inject both sources.
     *
     * @param clock  source of milliseconds
     * @param random source of the random bits
     */
    UuidV7(Clock clock, SecureRandom random) {
        this.clock = clock;
        this.random = random;
    }

    /** Returns a new UUID v7 from the shared default generator. */
    public static UUID next() {
        return DEFAULT.generate();
    }

    /**
     * The next identifier: timestamp of now, then random bits incremented within the same millisecond to stay ordered.
     */
    UUID generate() {
        lock.lock();
        try {
            long now = clock.millis();
            if (now > lastMillis) {
                lastMillis = now;
                randA = random.nextLong() & RAND_A_MASK;
                randB = random.nextLong() & RAND_B_MASK;
            } else {
                // Same millisecond, or the clock went back: keep ordering by incrementing the random part.
                randB = (randB + 1) & RAND_B_MASK;
                if (randB == 0) {
                    randA = (randA + 1) & RAND_A_MASK;
                    if (randA == 0) {
                        lastMillis++;
                    }
                }
            }
            long msb = (lastMillis << 16) | (0x7L << 12) | randA;
            long lsb = (0x2L << 62) | randB;
            return new UUID(msb, lsb);
        } finally {
            lock.unlock();
        }
    }
}
