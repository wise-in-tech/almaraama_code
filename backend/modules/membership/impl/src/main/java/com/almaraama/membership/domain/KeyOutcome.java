package com.almaraama.membership.domain;

/**
 * What the pre-registration key typed with a registration gave (D3, ADR-034). Glossary {@code KeyOutcome}.
 */
public enum KeyOutcome {
    /** No key typed. */
    NONE,
    /** Key and e-mail matched a pending pre-registration of the site: approved automatically. */
    MATCHED,
    /** Matched, but a planned unit was no longer free (E3, E4): the manager decides. */
    MATCHED_NEEDS_REVIEW,
    /** Wrong, expired or used key, or another e-mail: manual approval (the person is never told). */
    NOT_MATCHED
}
