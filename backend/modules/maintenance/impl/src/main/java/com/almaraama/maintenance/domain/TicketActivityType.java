package com.almaraama.maintenance.domain;

/** Changes of a ticket other than its steps (H9). Glossary {@code TicketActivityType}. */
public enum TicketActivityType {
    /** The ticket was reported. */
    CREATED,
    /** A photo was added (H10). */
    PHOTO_ADDED,
    /** A technician was designated by the routing of the type (H4). */
    TECHNICIAN_ROUTED
}
