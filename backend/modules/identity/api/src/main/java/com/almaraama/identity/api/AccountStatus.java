package com.almaraama.identity.api;

/** Status of an account (glossary {@code AccountStatus}). An account is never deleted, only deactivated (D7). */
public enum AccountStatus {
    /** Created by a manager; the person has not chosen a password yet (D4). */
    INVITED,
    /** Password chosen, e-mail not verified yet: cannot sign in (D1, self-registration in sprint 2). */
    PENDING_VERIFICATION,
    /** Can sign in. */
    ACTIVE,
    /** Cannot sign in; history kept (D7). */
    DEACTIVATED
}
