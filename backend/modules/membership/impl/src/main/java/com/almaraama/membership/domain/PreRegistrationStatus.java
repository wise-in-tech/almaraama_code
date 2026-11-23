package com.almaraama.membership.domain;

/**
 * State of a pre-registration (D3). {@code EXPIRED} is computed from the expiry date of a pending one, never stored.
 * Glossary {@code PreRegistrationStatus}.
 */
public enum PreRegistrationStatus {
    /** Waiting for the person to register with the key. */
    PENDING,
    /** The key was used once; it never works again. */
    USED,
    /** Cancelled by a manager or the promoteur (M2: kept). */
    CANCELLED,
    /** Pending past its expiry date (computed). */
    EXPIRED
}
