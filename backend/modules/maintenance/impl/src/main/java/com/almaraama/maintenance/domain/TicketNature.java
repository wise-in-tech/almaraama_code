package com.almaraama.maintenance.domain;

/** Nature of a ticket. Glossary {@code TicketNature}; {@code SNAG} comes with P3. */
public enum TicketNature {
    /** Reported by a person (H1). */
    CORRECTIVE,
    /** Created by a preventive rule (I1, sprint 4). */
    PREVENTIVE
}
