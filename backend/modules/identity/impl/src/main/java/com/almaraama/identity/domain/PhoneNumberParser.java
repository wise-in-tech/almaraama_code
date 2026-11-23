package com.almaraama.identity.domain;

import java.util.Optional;

/**
 * Out port: reads a phone number as a person typed it, in international form ({@code +33 6 12 34 56 78},
 * {@code 0033…}) or in the national form of the default country ({@code 0661 23 45 67}), and keeps it only when it
 * is a valid mobile number, since the account may receive SMS codes (D1, D4, ADR-028).
 */
public interface PhoneNumberParser {

    /** The number in E.164, or empty when it is not a valid mobile of its country. */
    Optional<PhoneNumber> tryParse(String raw);

    /** Throws {@code PHONE_INVALID} when the input is not a mobile number. */
    default PhoneNumber parse(String raw) {
        return tryParse(raw).orElseThrow(PhoneNumber::invalid);
    }
}
