package com.almaraama.sites.api;

/**
 * Kind of client organization (A1), the "what kind of client" of the platform; later hospital groups, companies…
 * Glossary {@code OrganizationType}.
 */
public enum OrganizationType {
    /** A promoteur with one or more residences. */
    PROPERTY_DEVELOPER,
    /** A residence that is a client on its own, without promoteur: one site only. */
    RESIDENCE
}
