package com.almaraama.files.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.nio.charset.StandardCharsets;
import org.junit.jupiter.api.Test;

/** ADR-038: the type of a file comes from its first bytes, never from its name; names never reach a path. */
class ContentCheckTest {

    @Test
    void ADR038_theTypeIsDetectedFromTheContent() {
        assertThat(ContentKind.detect(new byte[]{(byte) 0xFF, (byte) 0xD8, (byte) 0xFF, 0x00})).contains(ContentKind.JPEG);
        assertThat(ContentKind.detect(new byte[]{(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A}))
                .contains(ContentKind.PNG);
        assertThat(ContentKind.detect("%PDF-1.7".getBytes(StandardCharsets.US_ASCII))).contains(ContentKind.PDF);
        assertThat(ContentKind.detect("GIF89a".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ContentKind.detect("<svg onload=alert(1)>".getBytes(StandardCharsets.US_ASCII))).isEmpty();
        assertThat(ContentKind.detect(new byte[0])).isEmpty();
    }

    @Test
    void ADR038_aFileNameIsCleanedAndNeverKeepsAPath() {
        assertThat(FileNames.clean("../../etc/passwd")).isEqualTo("passwd");
        assertThat(FileNames.clean("C:\\photos\\fuite \"salle\".jpg")).isEqualTo("fuite salle.jpg");
        assertThat(FileNames.clean("..")).isNull();
        assertThat(FileNames.clean("a".repeat(300))).hasSize(120);
    }
}
