package com.almaraama.identity.adapters.out.phone;

import com.almaraama.identity.application.IdentityProperties;
import com.almaraama.identity.domain.PhoneNumber;
import com.almaraama.identity.domain.PhoneNumberParser;
import com.google.i18n.phonenumbers.NumberParseException;
import com.google.i18n.phonenumbers.PhoneNumberUtil;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberFormat;
import com.google.i18n.phonenumbers.PhoneNumberUtil.PhoneNumberType;
import java.util.Optional;
import java.util.Set;
import org.springframework.stereotype.Component;

/**
 * {@link PhoneNumberParser} backed by Google's libphonenumber (ADR-028): numbering plans of every country, updated
 * with the library. Only mobile numbers are kept; for countries whose plan cannot tell mobile from fixed lines
 * (United States, Canada…), such numbers are accepted too.
 */
@Component
class LibPhoneNumberParser implements PhoneNumberParser {

    private static final int MAX_INPUT_LENGTH = 30;
    private static final Set<PhoneNumberType> ACCEPTED =
            Set.of(PhoneNumberType.MOBILE, PhoneNumberType.FIXED_LINE_OR_MOBILE);

    private final PhoneNumberUtil util = PhoneNumberUtil.getInstance();
    private final String defaultRegion;

    /** @param properties gives the default country of numbers typed without their international prefix */
    LibPhoneNumberParser(IdentityProperties properties) {
        this.defaultRegion = properties.defaultPhoneRegion();
    }

    /** Parses with the country's numbering plan; empty when the input is too long, invalid or not a mobile. */
    @Override
    public Optional<PhoneNumber> tryParse(String raw) {
        if (raw == null || raw.isBlank() || raw.length() > MAX_INPUT_LENGTH) {
            return Optional.empty();
        }
        try {
            var number = util.parse(raw.strip(), defaultRegion);
            if (!util.isValidNumber(number) || !ACCEPTED.contains(util.getNumberType(number))) {
                return Optional.empty();
            }
            return Optional.of(new PhoneNumber(util.format(number, PhoneNumberFormat.E164)));
        } catch (NumberParseException notANumber) {
            return Optional.empty();
        }
    }
}
