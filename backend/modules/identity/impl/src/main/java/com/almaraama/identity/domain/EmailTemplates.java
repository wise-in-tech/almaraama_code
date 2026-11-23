package com.almaraama.identity.domain;

import com.almaraama.identity.api.events.AccountEmailKind;
import com.almaraama.shared.i18n.Language;
import java.util.Map;

/**
 * Out port: the texts of the account e-mails, per kind and language (ADR-031). Variables are written {@code {{name}}}
 * and their values are escaped in the HTML body.
 */
public interface EmailTemplates {

    /** A written e-mail: subject and both bodies. */
    record Rendered(String subject, String text, String html) {
    }

    /** Fills the template of this kind in this language with the variables. */
    Rendered render(AccountEmailKind kind, Language language, Map<String, String> variables);
}
