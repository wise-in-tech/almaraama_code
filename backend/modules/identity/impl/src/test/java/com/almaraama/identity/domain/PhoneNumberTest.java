package com.almaraama.identity.domain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.almaraama.shared.error.BusinessException;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

class PhoneNumberTest {

    @ParameterizedTest
    @ValueSource(strings = {"+213555123456", "+33612345678", "+14155552671", "+971501234567"})
    void D1_holdsAnInternationalNumberOfAnyCountry(String e164) {
        assertThat(new PhoneNumber(e164).value()).isEqualTo(e164);
    }

    @ParameterizedTest
    @ValueSource(strings = {"", "0555123456", "213555123456", "+0555123456", "+2135551234567890", "+213 555 12"})
    void D1_refusesWhatIsNotInE164Form(String raw) {
        assertThatThrownBy(() -> new PhoneNumber(raw))
                .isInstanceOf(BusinessException.class)
                .extracting(error -> ((BusinessException) error).code())
                .isEqualTo("PHONE_INVALID");
    }
}
