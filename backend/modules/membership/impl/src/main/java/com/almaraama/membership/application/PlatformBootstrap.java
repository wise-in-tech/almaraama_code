package com.almaraama.membership.application;

import com.almaraama.identity.api.IdentityApi;
import com.almaraama.identity.api.InvitationIssued;
import com.almaraama.identity.api.InvitePerson;
import com.almaraama.membership.api.events.RoleAssignedV1;
import com.almaraama.membership.domain.Role;
import com.almaraama.membership.domain.RoleAssignment;
import com.almaraama.membership.domain.RoleAssignmentRepository;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.i18n.Language;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.sites.api.CreateOrganization;
import com.almaraama.sites.api.OrganizationType;
import com.almaraama.sites.api.SitesApi;
import java.time.Clock;
import java.time.Instant;
import java.util.Optional;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Start-up tasks that give access without the API (ADR-026): our team's {@code PLATFORM_ADMIN} accounts from a
 * secret file, and, in development only, a demo organization. Idempotent and published for the audit log.
 */
@Service
public class PlatformBootstrap {

    private final IdentityApi identity;
    private final SitesApi sites;
    private final RoleCatalog catalog;
    private final RoleAssignmentRepository assignments;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the ports used at start-up. */
    PlatformBootstrap(IdentityApi identity, SitesApi sites, RoleCatalog catalog, RoleAssignmentRepository assignments,
                      ApplicationEventPublisher events, Clock clock) {
        this.identity = identity;
        this.sites = sites;
        this.catalog = catalog;
        this.assignments = assignments;
        this.events = events;
        this.clock = clock;
    }

    /**
     * Makes the person with this e-mail a platform admin (ADR-026); when they have no password yet, a "set my
     * password" e-mail is sent (ADR-031). {@code phone} is mandatory for every account (D1). Idempotent.
     */
    @Transactional
    public InvitationIssued ensurePlatformAdmin(String email, String phone, String firstName, String lastName) {
        InvitationIssued issued = identity.invitePerson(new InvitePerson(firstName, lastName, email, phone,
                Language.AR, Actor.platform()));
        Instant now = clock.instant();
        Role role = catalog.require(RoleCatalog.SEED_PLATFORM_ADMIN);
        if (assignments.active(issued.personId(), role.code(), ScopeType.PLATFORM, null, null).isEmpty()) {
            RoleAssignment assignment = assignments.save(RoleAssignment.onPlatform(issued.personId(), role,
                    Actor.platform(), now));
            events.publishEvent(new RoleAssignedV1(assignment.id(), issued.personId(), role.code(),
                    ScopeType.PLATFORM, Optional.empty(), Optional.empty(), Actor.platform(), now));
        }
        return issued;
    }

    /** Development only: an Algerian demo organization owned by this person, when they hold no grants-all role yet. */
    @Transactional
    public void ensureDemoOrganization(PersonId owner) {
        boolean hasOne = assignments.activeOfPerson(owner).stream().anyMatch(a -> catalog.grantsAll(a.role()));
        if (hasOne) {
            return;
        }
        Instant now = clock.instant();
        Role role = catalog.require(RoleCatalog.SEED_ORGANIZATION_ADMIN);
        OrganizationId organization = sites.createOrganization(new CreateOrganization(
                OrganizationType.PROPERTY_DEVELOPER, "Promoteur démo", "DZ", null, null, Actor.platform()));
        RoleAssignment assignment = assignments.save(RoleAssignment.onOrganization(owner, role, organization,
                Actor.platform(), now));
        events.publishEvent(new RoleAssignedV1(assignment.id(), owner, role.code(), ScopeType.ORGANIZATION,
                Optional.of(organization), Optional.empty(), Actor.platform(), now));
    }
}
