package com.almaraama.identity.api;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.i18n.Language;
import java.util.Objects;

/**
 * Who to create an account for (D1, D4): the e-mail is the sign-in identifier, the phone is mandatory but not an
 * identifier (ADR-030).
 */
public record InvitePerson(String firstName, String lastName, String email, String phone, Language language,
                           Actor issuedBy) {

    /** Every field is mandatory; the identity module checks the e-mail and phone formats. */
    public InvitePerson {
        Objects.requireNonNull(firstName, "firstName");
        Objects.requireNonNull(lastName, "lastName");
        Objects.requireNonNull(email, "email");
        Objects.requireNonNull(phone, "phone");
        Objects.requireNonNull(language, "language");
        Objects.requireNonNull(issuedBy, "issuedBy");
    }
}
