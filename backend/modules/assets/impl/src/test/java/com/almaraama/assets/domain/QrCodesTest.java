package com.almaraama.assets.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.HashSet;
import java.util.Set;
import org.junit.jupiter.api.Test;

/** G4, ADR-039: the QR code of an equipment is 26 random base32 characters. */
class QrCodesTest {

    @Test
    void G4_qrCodesAreRandomBase32OfTwentySixCharacters() {
        Set<String> codes = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String code = QrCodes.next();
            assertThat(QrCodes.wellFormed(code)).isTrue();
            codes.add(code);
        }
        assertThat(codes).hasSize(1000);
        assertThat(QrCodes.wellFormed("abc")).isFalse();
        assertThat(QrCodes.wellFormed(null)).isFalse();
    }
}
