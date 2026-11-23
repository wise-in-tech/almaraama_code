package com.almaraama.shared.id;

import static org.assertj.core.api.Assertions.assertThat;

import java.security.SecureRandom;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;
import org.junit.jupiter.api.Test;

class UuidV7Test {

    @Test
    void CF10_generatesVersion7WithRfc9562Variant() {
        UUID id = UuidV7.next();

        assertThat(id.version()).isEqualTo(7);
        assertThat(id.variant()).isEqualTo(2);
    }

    @Test
    void CF10_carriesTheCreationTimeInMilliseconds() {
        Instant now = Instant.parse("2026-11-12T10:15:30.123Z");
        UuidV7 generator = new UuidV7(Clock.fixed(now, ZoneOffset.UTC), new SecureRandom());

        UUID id = generator.generate();

        assertThat(id.getMostSignificantBits() >>> 16).isEqualTo(now.toEpochMilli());
    }

    @Test
    void CF10_idsFromTheSameMillisecondStayOrdered() {
        UuidV7 generator = new UuidV7(Clock.fixed(Instant.now(), ZoneOffset.UTC), new SecureRandom());
        List<UUID> ids = new ArrayList<>();
        for (int i = 0; i < 10_000; i++) {
            ids.add(generator.generate());
        }

        // Compared as strings: the canonical form sorts like PostgreSQL's uuid type.
        assertThat(ids.stream().map(UUID::toString).toList()).isSorted().doesNotHaveDuplicates();
    }
}
