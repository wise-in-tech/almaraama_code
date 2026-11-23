package com.almaraama.membership.application;

import com.almaraama.membership.api.events.SupportSessionEndedV1;
import com.almaraama.membership.api.events.SupportSessionStartedV1;
import com.almaraama.membership.domain.SupportSession;
import com.almaraama.membership.domain.SupportSessionRepository;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.OrganizationSummary;
import com.almaraama.sites.api.SitesApi;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Support sessions of the platform administrators (C12, ADR-035): one organization, a mandatory reason, one hour,
 * read and write with the promoteur's permissions; the coded rules still apply (M3, C6, J3, M2). Opening one ends the
 * administrator's previous session. Every action done in it carries the session (actor) in the audit log.
 */
@Service
public class SupportSessionService {

    private static final Logger LOG = LoggerFactory.getLogger(SupportSessionService.class);

    private final SupportSessionRepository sessions;
    private final SitesApi sites;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * @param sessions the support sessions
     * @param sites    organizations (they must exist and be active)
     * @param events   publishes the opening and the end for the audit log
     * @param clock    time of the session
     */
    SupportSessionService(SupportSessionRepository sessions, SitesApi sites, ApplicationEventPublisher events,
                          Clock clock) {
        this.sessions = sessions;
        this.sites = sites;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Opens a session; needs {@code support:access} on the platform. {@code SUPPORT_REASON_INVALID}, {@code
     * NOT_FOUND} (unknown organization), {@code ORGANIZATION_NOT_ACTIVE}. Publishes {@code SupportSessionStartedV1}
     * (and {@code SupportSessionEndedV1} for the previous one).
     */
    @Transactional
    public SupportSession start(Perimeter perimeter, OrganizationId organizationId, String reason) {
        perimeter.requireOnPlatform(Permission.SUPPORT_ACCESS);
        OrganizationSummary organization = sites.findOrganization(organizationId)
                .orElseThrow(RoleAdministration::notFound);
        if (!organization.active()) {
            throw new BusinessException("ORGANIZATION_NOT_ACTIVE", ErrorCategory.RULE_VIOLATION, "Deactivated");
        }
        PersonId administrator = new PersonId(perimeter.actor().id());
        Instant now = clock.instant();
        SupportSession session = SupportSession.start(administrator, organizationId, reason, now);
        sessions.openOf(administrator, now).ifPresent(previous -> end(previous, now));
        sessions.save(session);
        events.publishEvent(new SupportSessionStartedV1(session.id(), administrator, organizationId,
                session.reason(), session.expiresAt(), now));
        LOG.info("Support session {} opened by person {} on organization {} until {}", session.id(),
                administrator.value(), organizationId.value(), session.expiresAt());
        return session;
    }

    /**
     * Ends a session early; needs {@code support:access}; only its administrator ({@code NOT_FOUND} for anyone else).
     * Ending an ended or expired session changes nothing.
     */
    @Transactional
    public SupportSession end(Perimeter perimeter, UUID sessionId) {
        perimeter.requireOnPlatform(Permission.SUPPORT_ACCESS);
        SupportSession session = sessions.findById(sessionId)
                .filter(s -> s.personId().value().equals(perimeter.actor().id()))
                .orElseThrow(RoleAdministration::notFound);
        end(session, clock.instant());
        return session;
    }

    /** Sessions of every administrator, newest first; needs {@code support:access}. */
    @Transactional(readOnly = true)
    public PageResult<SupportSession> list(Perimeter perimeter, Optional<OrganizationId> organization,
                                           boolean openOnly, PageQuery page) {
        perimeter.requireOnPlatform(Permission.SUPPORT_ACCESS);
        Instant now = clock.instant();
        List<SupportSession> found = sessions.list(organization).stream()
                .filter(s -> !openOnly || s.isOpen(now)).toList();
        int from = (int) Math.min(page.offset(), found.size());
        int to = Math.min(from + page.size(), found.size());
        return new PageResult<>(found.subList(from, to), page.page(), page.size(), found.size());
    }

    /** The caller's open session, for {@code GET /v1/me/access}. */
    @Transactional(readOnly = true)
    public Optional<SupportSession> openOf(Perimeter perimeter) {
        return sessions.openOf(new PersonId(perimeter.actor().id()), clock.instant());
    }

    /** Whether an organization has an open support session (ADR-035: it cannot be deactivated meanwhile). */
    @Transactional(readOnly = true)
    public boolean openOn(OrganizationId organization) {
        return !sessions.openOn(organization, clock.instant()).isEmpty();
    }

    /** The current instant, to compute a session's status. */
    public Instant now() {
        return clock.instant();
    }

    /** Ends an open session and publishes {@code SupportSessionEndedV1}. */
    private void end(SupportSession session, Instant now) {
        if (session.end(now)) {
            sessions.save(session);
            events.publishEvent(new SupportSessionEndedV1(session.id(), session.personId(), session.organizationId(),
                    now));
            LOG.info("Support session {} ended", session.id());
        }
    }
}
