package com.almaraama.identity.adapters.out.phone;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.almaraama.identity.application.IdentityProperties;
import com.almaraama.identity.domain.PhoneNumber;
import com.almaraama.shared.error.BusinessException;
import java.time.Duration;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;

class LibPhoneNumberParserTest {

    private final LibPhoneNumberParser parser = new LibPhoneNumberParser(new IdentityProperties(
            Duration.ofMinutes(10), Duration.ofDays(30), Duration.ofDays(7), Duration.ofHours(48), Duration.ofHours(1),
            3, "http://localhost:5173", "DZ",
            new IdentityProperties.Jwt("almaraama", null, true)));

    @ParameterizedTest
    @ValueSource(strings = {"+213555123456", "00213555123456", "0555123456", "0555 12 34 56", "0555.12.34.56",
            "+213 555-12-34-56"})
    void D1_acceptsTheUsualWaysOfWritingAnAlgerianMobile(String raw) {
        assertThat(parser.parse(raw).value()).isEqualTo("+213555123456");
    }

    @ParameterizedTest
    @CsvSource({
            "+33 6 12 34 56 78, +33612345678",
            "0033612345678, +33612345678",
            "+971 50 123 4567, +971501234567",
            "+216 20 123 456, +21620123456",
            "+1 415 555 2671, +14155552671"})
    void D1_acceptsMobilesOfEveryCountryWithTheirInternationalPrefix(String raw, String e164) {
        assertThat(parser.parse(raw)).isEqualTo(new PhoneNumber(e164));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0123", "0255123456", "05551234567", "phone", "+213855123456", "+33 1 23 45 67 89",
            "0612345678901234567890123456789"})
    void D1_refusesWhatIsNotAMobileNumber(String raw) {
        assertThat(parser.tryParse(raw)).isEmpty();
        assertThatThrownBy(() -> parser.parse(raw))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code())
                .isEqualTo("PHONE_INVALID");
    }

    @Test
    void D1_aNationalNumberIsReadInTheDefaultCountry() {
        // 0612345678 is a French mobile written nationally, but the default country is Algeria: +213 6 12 34 56 78.
        assertThat(parser.parse("0612345678").value()).isEqualTo("+213612345678");
    }
}
