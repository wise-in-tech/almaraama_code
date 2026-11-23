package com.almaraama.membership.domain;

/**
 * Nature of a role (ADR-026 revision 2). It decides one thing only: who may give the role (RB-1). Without {@code
 * role-assignment:grant}, a person can give a role of kind {@code OCCUPANT} (with {@code account:invite} or {@code
 * access:approve}) and, from sprint 4, {@code TECHNICIAN} (with {@code technician:create}). Glossary {@code RoleKind}.
 */
public enum RoleKind {
    /** Our team (platform administrators). */
    PLATFORM,
    /** The promoteur, highest role of an organization. */
    ADMINISTRATION,
    /** Management staff of a residence (gestionnaire), later staff of other uses. */
    STAFF,
    /** External technician (C3). */
    TECHNICIAN,
    /** Resident, later buyer: no management permission (C4). */
    OCCUPANT
}
