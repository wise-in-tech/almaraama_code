package com.almaraama.membership.domain;

/** How a site access was created. Glossary {@code MembershipOrigin}. */
public enum MembershipOrigin {
    INVITATION,
    SELF_REGISTRATION,
    /** Self-registration approved automatically by a pre-registration key (D3, ADR-034). */
    PRE_REGISTRATION,
    DIRECT_CREATION
}
