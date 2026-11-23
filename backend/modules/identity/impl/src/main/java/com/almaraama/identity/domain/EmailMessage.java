package com.almaraama.identity.domain;

/**
 * An e-mail ready to send (ADR-031). Never printed: it holds the address and, for links, the secret.
 *
 * @param to      the recipient
 * @param subject subject line
 * @param text    plain text body
 * @param html    simple HTML body, values already escaped
 */
public record EmailMessage(EmailAddress to, String subject, String text, String html) {

    /** Hides the address, the subject and the bodies, which may hold a link secret (ADR-033). */
    @Override
    public String toString() {
        return "EmailMessage[to ***@" + to.domain() + ", content hidden]";
    }
}
