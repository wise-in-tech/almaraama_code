package com.almaraama.membership.adapters.out.persistence;

import com.almaraama.membership.domain.SupportSession;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Support sessions, schema membership. No delete (M2). */
interface SpringDataSupportSessions extends Repository<SupportSession, UUID> {

    /** Loads a session. */
    Optional<SupportSession> findById(UUID id);

    /** Sessions of an administrator not ended and expiring after this instant, newest first. */
    List<SupportSession> findByPersonIdAndEndedAtIsNullAndExpiresAtAfterOrderByStartedAtDesc(UUID personId,
                                                                                             Instant now);

    /** Sessions on an organization not ended and expiring after this instant. */
    List<SupportSession> findByOrganizationIdAndEndedAtIsNullAndExpiresAtAfter(UUID organizationId, Instant now);

    /** Every session, newest first. */
    List<SupportSession> findAllByOrderByStartedAtDesc();

    /** Sessions on an organization, newest first. */
    List<SupportSession> findByOrganizationIdOrderByStartedAtDesc(UUID organizationId);

    /** Inserts or updates a session. */
    SupportSession save(SupportSession session);
}
