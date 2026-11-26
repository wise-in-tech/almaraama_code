package com.almaraama.workflow.api;

/**
 * Fixed category of a circuit step (ADR-036), the same for every circuit: notifications (K1, K3), filters,
 * indicators and the preventive rule I3 rely on it, whatever the client's circuit. Glossary {@code NodeCategory}.
 */
public enum NodeCategory {
    /** Asked, not taken yet. */
    OPEN,
    /** Being handled. */
    IN_PROGRESS,
    /** Waiting (part, quote, access…). */
    ON_HOLD,
    /** Handled, waiting for closing. */
    RESOLVED,
    /** Closed: the end. */
    CLOSED,
    /** Cancelled: the end. */
    CANCELLED;

    /** Whether the subject has finished its circuit. */
    public boolean terminal() {
        return this == CLOSED || this == CANCELLED;
    }
}
