package com.almaraama.membership.domain;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Out port: support sessions of the platform administrators (C12, ADR-035). No delete (M2). */
public interface SupportSessionRepository {

    /** One session. */
    Optional<SupportSession> findById(UUID id);

    /** The administrator's open session at this instant, if any (at most one). */
    Optional<SupportSession> openOf(PersonId administrator, Instant now);

    /** Open sessions on an organization at this instant. */
    List<SupportSession> openOn(OrganizationId organization, Instant now);

    /** Sessions, newest first, optionally of one organization. */
    List<SupportSession> list(Optional<OrganizationId> organization);

    /** Inserts or updates a session. */
    SupportSession save(SupportSession session);
}
