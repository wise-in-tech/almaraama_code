package com.almaraama.identity.domain;

import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * A mobile number in international E.164 form ({@code +213661234567}, {@code +33612345678}), the account identifier
 * (D1). Any country is accepted (ADR-028). Turning what a person typed into this form, and checking that the
 * number really is a mobile of its country, is the job of {@link PhoneNumberParser}.
 */
public record PhoneNumber(String value) {

    private static final Pattern E164 = Pattern.compile("\\+[1-9]\\d{6,14}");

    public PhoneNumber {
        Objects.requireNonNull(value, "value");
        if (!E164.matcher(value).matches()) {
            throw invalid();
        }
    }

    /** The last digits, used to refuse a PIN equal to the end of the phone number. */
    public String lastDigits(int count) {
        return value.substring(value.length() - count);
    }

    /** {@code PHONE_INVALID}: not a mobile number in international form. */
    static BusinessException invalid() {
        return new BusinessException("PHONE_INVALID", ErrorCategory.VALIDATION, "Not a mobile number");
    }
}
