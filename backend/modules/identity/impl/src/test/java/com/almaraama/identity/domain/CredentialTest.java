package com.almaraama.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;

import com.almaraama.shared.id.PersonId;
import java.time.Duration;
import java.time.Instant;
import org.junit.jupiter.api.Test;

class CredentialTest {

    private static final Instant NOW = Instant.parse("2026-11-15T10:00:00Z");

    @Test
    void D4_fourFailuresCostNothingThenTheLockGrows() {
        Credential credential = Credential.create(PersonId.newId(), "hash", NOW);
        for (int i = 1; i <= LockoutPolicy.FREE_ATTEMPTS; i++) {
            assertThat(credential.recordFailure(NOW)).isEmpty();
        }
        assertThat(credential.isLocked(NOW)).isFalse();

        assertThat(credential.recordFailure(NOW)).contains(NOW.plus(Duration.ofMinutes(1)));
        assertThat(credential.isLocked(NOW.plusSeconds(59))).isTrue();
        assertThat(credential.isLocked(NOW.plusSeconds(60))).isFalse();

        assertThat(credential.recordFailure(NOW)).contains(NOW.plus(Duration.ofMinutes(5)));
        assertThat(credential.recordFailure(NOW)).contains(NOW.plus(Duration.ofMinutes(15)));
        assertThat(credential.recordFailure(NOW)).contains(NOW.plus(Duration.ofHours(1)));
        assertThat(credential.recordFailure(NOW)).contains(NOW.plus(Duration.ofHours(1)));
    }

    @Test
    void D4_aSuccessfulSignInResetsTheCount() {
        Credential credential = Credential.create(PersonId.newId(), "hash", NOW);
        for (int i = 0; i < 5; i++) {
            credential.recordFailure(NOW);
        }
        credential.recordSuccess(NOW);

        assertThat(credential.isLocked(NOW)).isFalse();
        assertThat(credential.failedAttempts()).isZero();
        assertThat(credential.recordFailure(NOW)).isEmpty();
    }
}
