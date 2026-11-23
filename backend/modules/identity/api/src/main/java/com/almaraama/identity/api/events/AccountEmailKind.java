package com.almaraama.identity.api.events;

/**
 * E-mails of the accounts (ADR-031), the only e-mails the platform sends (spec v3.4, delivery principle 4). Glossary
 * {@code AccountTokenPurpose} and {@code EmailDispatch}.
 */
public enum AccountEmailKind {
    /** "Set my password" link of an account created by a manager (D4), 7 days; also verifies the e-mail. */
    SET_PASSWORD,
    /** E-mail verification link (D1), 48 hours. */
    EMAIL_VERIFICATION,
    /** Password reset link (D5), 1 hour. */
    PASSWORD_RESET,
    /** Notice that the password was changed (ASVS V2.2.3); carries no link. */
    PASSWORD_CHANGED,
    /**
     * "You already have an account": someone registered on a site's QR code with this e-mail (D2, ADR-034); tells the
     * person to sign in and scan again, or to reset their password. Carries no link.
     */
    ALREADY_REGISTERED;

    /** Whether this e-mail carries a single-use link. */
    public boolean carriesLink() {
        return this != PASSWORD_CHANGED && this != ALREADY_REGISTERED;
    }
}
