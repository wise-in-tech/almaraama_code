package com.almaraama.notification.domain;

/** What happened; the application writes the text in the reader's language (K1). Glossary {@code NotificationTemplate}. */
public enum NotificationTemplate {
    ACCESS_REQUESTED,
    /** An access approved automatically by a pre-registration key (D3), for the managers. */
    ACCESS_AUTO_APPROVED,
    ACCESS_APPROVED,
    ACCESS_REJECTED,

    /** A ticket was created on a site the reader manages (K1). */
    TICKET_CREATED,

    /** A ticket was given to the reader by the routing of its type (H4). */
    TICKET_ROUTED,

    /** A ticket the reader asked for, manages or works on changed step (K1, K3). */
    TICKET_STATUS_CHANGED
}
