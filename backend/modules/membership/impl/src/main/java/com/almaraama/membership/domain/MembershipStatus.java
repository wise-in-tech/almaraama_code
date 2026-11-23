package com.almaraama.membership.domain;

/** Status of a site access (UML state diagram 5.2). Glossary {@code MembershipStatus}. */
public enum MembershipStatus {
    PENDING,
    ACTIVE,
    REJECTED,
    DEACTIVATED
}
