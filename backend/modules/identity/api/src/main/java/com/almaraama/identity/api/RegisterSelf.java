package com.almaraama.identity.api;

import com.almaraama.shared.i18n.Language;
import java.util.Objects;

/**
 * A person registering on the public form of a site's QR code (D2, ADR-034): their details and the password they
 * chose. The password is checked by the identity module (ADR-030) and never kept anywhere else.
 */
public record RegisterSelf(String firstName, String lastName, String email, String phone, Language language,
                           String password) {

    /** Every field is mandatory; the identity module checks the formats and the password rules. */
    public RegisterSelf {
        Objects.requireNonNull(firstName, "firstName");
        Objects.requireNonNull(lastName, "lastName");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(password, "password");
    }

    /** Never prints the password. */
    @Override
    public String toString() {
        return "RegisterSelf[language=" + language + "]";
    }
}
