package com.almaraama.maintenance.domain;

/** Who sees a ticket (H8). Glossary {@code Visibility}. */
public enum Visibility {
    /** Common parts: every member of the residence, read only. */
    COMMON,
    /** Private or sensitive: requester, occupants of the unit, managers, promoteur, assigned technicians. */
    PRIVATE
}
