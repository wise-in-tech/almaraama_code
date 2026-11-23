package com.almaraama.notification.domain;

/** What happened; the application writes the text in the reader's language (K1). Glossary {@code NotificationTemplate}. */
public enum NotificationTemplate {
    ACCESS_REQUESTED,
    /** An access approved automatically by a pre-registration key (D3), for the managers. */
    ACCESS_AUTO_APPROVED,
    ACCESS_APPROVED,
    ACCESS_REJECTED
}
