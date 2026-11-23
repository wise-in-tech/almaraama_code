package com.almaraama.identity.api;

import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.PersonId;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/**
 * Public port of the identity module (CF-1). Other modules create accounts, send password links and read public
 * profiles here, and never see credentials, devices, links or tokens (ADR-030).
 */
public interface IdentityApi {

    /**
     * Creates the account of a person by e-mail (D4) and sends them a "set my password" link (7 days) by e-mail.
     * When the e-mail already has an account (active or waiting for its verification), nothing is sent and the result
     * says so; an account still {@code INVITED} gets a new link. Throws {@code ACCOUNT_DEACTIVATED} for a deactivated
     * account (D7), {@code EMAIL_INVALID} or {@code PHONE_INVALID} for bad input.
     */
    InvitationIssued invitePerson(InvitePerson command);

    /**
     * Sends a new password link to a person, by e-mail only (D4, D5): "set my password" for an {@code INVITED} account,
     * a reset link for an active one. Previous links of the same kind stop working. Throws {@code NOT_FOUND} for an
     * unknown person and {@code ACCOUNT_DEACTIVATED} for a deactivated account. The caller checks the permission.
     */
    PasswordLinkIssued sendPasswordLink(PersonId personId, Actor requestedBy);

    /**
     * Self-registration on a site's QR code (D2, ADR-034). For an unknown e-mail, creates the account in {@code
     * PENDING_VERIFICATION} with the chosen password and sends the "verify my e-mail" link; returns its identifier.
     * For a known e-mail, changes nothing, sends the "you already have an account" e-mail and returns empty: the
     * caller must answer the public form exactly as in the first case. Errors, checked before the e-mail is looked
     * up so they reveal nothing: {@code PASSWORD_TOO_SHORT}, {@code PASSWORD_TOO_LONG}, {@code PASSWORD_TOO_COMMON},
     * {@code PASSWORD_SAME_AS_EMAIL}, {@code EMAIL_INVALID}, {@code PHONE_INVALID}.
     */
    Optional<PersonId> registerSelf(RegisterSelf command);

    /**
     * Checks an e-mail and a phone number and returns them in their stored form, without creating anything (D3
     * pre-registrations); {@code EMAIL_INVALID}, {@code PHONE_INVALID}.
     */
    Contact checkContact(String email, String phone);

    /** The public profile of a person, without credentials. */
    Optional<PersonSummary> findPerson(PersonId personId);

    /** The public profiles of several persons, in one call; unknown identifiers are left out. */
    List<PersonSummary> findPersons(Collection<PersonId> personIds);

    /** The person who signs in with this e-mail, compared trimmed and in lower case (D1, C3). */
    Optional<PersonSummary> findPersonByEmail(String email);
}
