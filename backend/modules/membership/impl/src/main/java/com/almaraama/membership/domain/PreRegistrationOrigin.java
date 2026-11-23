package com.almaraama.membership.domain;

/** How a pre-registration was created (D3, ADR-027). Glossary {@code PreRegistrationOrigin}. */
public enum PreRegistrationOrigin {
    /** Typed in the application by a manager or the promoteur. */
    MANUAL,
    /** One person of a resident import. */
    IMPORT
}
