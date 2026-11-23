package com.almaraama.sites.domain;

/** Status of a zone (A8). Glossary {@code ZoneStatus}. */
public enum ZoneStatus {
    /** From the reference data. */
    VALIDATED,
    /** Created by a promoteur while the country is not loaded; validated or merged by the administrators later. */
    PROVISIONAL,
    /** Kept, never deleted (M2), no longer offered. */
    DEACTIVATED
}
