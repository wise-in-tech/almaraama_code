package com.almaraama.sites.domain;

/** Where a current address comes from (A9). Glossary {@code AddressOrigin}. */
public enum AddressOrigin {
    /** Typed for this owner (a site, or a building with its own street), or edited later. */
    OWN,
    /** A building's copy of its site's address, made at its creation and unchanged since. */
    SITE_COPY
}
