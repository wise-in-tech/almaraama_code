package com.almaraama.membership.application;

import com.almaraama.identity.api.IdentityApi;
import com.almaraama.identity.api.PersonSummary;
import com.almaraama.membership.api.PerimeterApi;
import com.almaraama.membership.api.events.MembershipApprovedV1;
import com.almaraama.membership.api.events.MembershipDeactivatedV1;
import com.almaraama.membership.api.events.MembershipRejectedV1;
import com.almaraama.membership.domain.MembershipOrigin;
import com.almaraama.membership.domain.MembershipStatus;
import com.almaraama.membership.domain.Occupancy;
import com.almaraama.membership.domain.OccupancyRepository;
import com.almaraama.membership.domain.Role;
import com.almaraama.membership.domain.RoleAssignment;
import com.almaraama.membership.domain.RoleKind;
import com.almaraama.membership.domain.RoleAssignmentRepository;
import com.almaraama.membership.domain.SiteMembership;
import com.almaraama.membership.domain.SiteMembershipRepository;
import com.almaraama.membership.domain.ValidationStatus;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.SiteSummary;
import com.almaraama.sites.api.SitesApi;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Site accesses (D2, D6, C7, E5): decisions on requests, building restriction, deactivation, and the activation of
 * invited residents when they choose their password. Requests themselves come from {@link RegistrationService}.
 */
@Service
public class MembershipService {

    /** An access with the person's public profile. */
    public record MembershipView(SiteMembership membership, PersonSummary person) {
    }

    private final SiteMembershipRepository memberships;
    private final RoleAssignmentRepository assignments;
    private final OccupancyRepository occupancyRepository;
    private final RoleAdministration roles;
    private final RoleCatalog catalog;
    private final OccupancyBook occupancies;
    private final IdentityApi identity;
    private final SitesApi sites;
    private final PerimeterApi perimeters;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the ports and collaborators of the site access use cases. */
    MembershipService(SiteMembershipRepository memberships, RoleAssignmentRepository assignments,
                      RoleCatalog catalog,
                      OccupancyRepository occupancyRepository, RoleAdministration roles, OccupancyBook occupancies,
                      IdentityApi identity, SitesApi sites, PerimeterApi perimeters,
                      ApplicationEventPublisher events, Clock clock) {
        this.memberships = memberships;
        this.assignments = assignments;
        this.occupancyRepository = occupancyRepository;
        this.roles = roles;
        this.catalog = catalog;
        this.occupancies = occupancies;
        this.identity = identity;
        this.sites = sites;
        this.perimeters = perimeters;
        this.events = events;
        this.clock = clock;
    }

    // --------------------------------------------------------------------------------------- site side

    /**
     * Accesses of a site filtered by status and by name or phone; needs {@code account:read}. A self-registration
     * not submitted yet (e-mail not verified, ADR-034) appears nowhere.
     */
    @Transactional(readOnly = true)
    public PageResult<MembershipView> ofSite(Perimeter perimeter, SiteId siteId, MembershipStatus status, String q,
                                             PageQuery page) {
        SiteSummary site = site(siteId);
        perimeter.require(Permission.ACCOUNT_READ, site.organizationId(), siteId);
        List<SiteMembership> found = memberships.ofSite(siteId, status).stream()
                .filter(MembershipService::visibleToManagers).toList();
        Map<PersonId, PersonSummary> people = people(found);
        String filter = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        String digits = filter.replaceAll("\\D", "");
        List<MembershipView> views = found.stream()
                .map(m -> new MembershipView(m, people.get(m.personId())))
                .filter(v -> v.person() != null)
                .filter(v -> filter.isEmpty()
                        || (v.person().firstName() + " " + v.person().lastName()).toLowerCase(Locale.ROOT)
                        .contains(filter)
                        || !digits.isEmpty() && v.person().phone().contains(digits))
                .toList();
        int from = (int) Math.min(page.offset(), views.size());
        int to = Math.min(from + page.size(), views.size());
        return new PageResult<>(views.subList(from, to), page.page(), page.size(), views.size());
    }

    /**
     * D2 step 3, E5: opens the chosen buildings (C7; none chosen = the buildings of the given units, or every building
     * when no unit is given), gives RESIDENT and records the units chosen by the approver, each as owner or tenant.
     * Needs {@code access:approve}, and {@code occupancy:create} with units; {@code VERSION_CONFLICT}, {@code
     * MEMBERSHIP_NOT_PENDING}, {@code BUILDING_NOT_IN_SITE}, {@code UNIT_NOT_IN_SITE}, E3 and E4 codes. Publishes
     * {@code MembershipApprovedV1}.
     */
    @Transactional
    public MembershipView approve(Perimeter perimeter, UUID membershipId, List<UUID> allowedBuildings,
                                  List<OccupancyRequest> units, long version) {
        SiteMembership membership = loadVisible(membershipId);
        perimeter.require(Permission.ACCESS_APPROVE, membership.organizationId(), membership.siteId());
        if (!units.isEmpty()) {
            perimeter.require(Permission.OCCUPANCY_CREATE, membership.organizationId(), membership.siteId());
        }
        checkVersion(membership, version);
        Instant now = clock.instant();
        PersonId by = RoleAdministration.actor(perimeter);
        Set<BuildingId> buildings = allowedBuildings.isEmpty() && !units.isEmpty()
                ? occupancies.buildingsOfUnits(membership.siteId(), units)
                : occupancies.buildingsOf(membership.siteId(), allowedBuildings);
        membership.approve(buildings, by, now);
        memberships.save(membership);
        giveResident(perimeter, membership, now);
        for (OccupancyRequest unit : units) {
            occupancies.start(membership.personId(), membership.organizationId(), membership.siteId(), unit,
                    ValidationStatus.VALIDATED, by, now);
        }
        events.publishEvent(new MembershipApprovedV1(membership.id(), membership.organizationId(),
                membership.siteId(), membership.personId(), site(membership.siteId()).name(), perimeter.actor(),
                now));
        return view(membership);
    }

    /**
     * Rejects a pending request, kept as REJECTED; needs {@code access:approve}; {@code MEMBERSHIP_NOT_PENDING}.
     * Publishes {@code MembershipRejectedV1}.
     */
    @Transactional
    public MembershipView reject(Perimeter perimeter, UUID membershipId, String reason) {
        SiteMembership membership = loadVisible(membershipId);
        perimeter.require(Permission.ACCESS_REFUSE, membership.organizationId(), membership.siteId());
        Instant now = clock.instant();
        membership.reject(RoleAdministration.actor(perimeter), reason, now);
        memberships.save(membership);
        events.publishEvent(new MembershipRejectedV1(membership.id(), membership.organizationId(),
                membership.siteId(), membership.personId(), site(membership.siteId()).name(), perimeter.actor(),
                now));
        return view(membership);
    }

    /** C7: empty means every building of the site. */
    @Transactional
    public MembershipView restrict(Perimeter perimeter, UUID membershipId, List<UUID> buildings, long version) {
        SiteMembership membership = load(membershipId);
        perimeter.require(Permission.ACCESS_RESTRICT, membership.organizationId(), membership.siteId());
        checkVersion(membership, version);
        membership.restrictTo(occupancies.buildingsOf(membership.siteId(), buildings), clock.instant());
        memberships.save(membership);
        return view(membership);
    }

    /**
     * Ends an access and its RESIDENT role, keeping the history (D6); needs {@code access:revoke}; {@code
     * MEMBERSHIP_NOT_ACTIVE}.
     */
    @Transactional
    public MembershipView deactivate(Perimeter perimeter, UUID membershipId, String reason) {
        SiteMembership membership = load(membershipId);
        perimeter.require(Permission.ACCESS_REVOKE, membership.organizationId(), membership.siteId());
        Instant now = clock.instant();
        membership.deactivate(RoleAdministration.actor(perimeter), reason, now);
        memberships.save(membership);
        assignments.activeOfPerson(membership.personId()).stream()
                .filter(a -> a.scopeType() == ScopeType.SITE && a.siteId().equals(Optional.of(membership.siteId()))
                        && catalog.isKind(a.role(), RoleKind.OCCUPANT))
                .forEach(a -> roles.endAssignment(perimeter, a, reason, now));
        events.publishEvent(new MembershipDeactivatedV1(membership.id(), membership.organizationId(),
                membership.siteId(), membership.personId(), perimeter.actor(), now));
        return view(membership);
    }

    /**
     * Gives back a deactivated access and its RESIDENT role; needs {@code access:revoke}; {@code
     * MEMBERSHIP_NOT_DEACTIVATED}.
     */
    @Transactional
    public MembershipView reactivate(Perimeter perimeter, UUID membershipId) {
        SiteMembership membership = load(membershipId);
        perimeter.require(Permission.ACCESS_APPROVE, membership.organizationId(), membership.siteId());
        Instant now = clock.instant();
        membership.reactivate(RoleAdministration.actor(perimeter), now);
        memberships.save(membership);
        giveResident(perimeter, membership, now);
        return view(membership);
    }

    // ------------------------------------------------------------------------------------- person side (D3)

    /** The caller's accesses, newest first. */
    @Transactional(readOnly = true)
    public List<SiteMembership> mine(Perimeter perimeter) {
        return memberships.ofPerson(new PersonId(perimeter.actor().id()));
    }

    /** The caller's current occupancies in a site. */
    @Transactional(readOnly = true)
    public List<Occupancy> myOccupancies(Perimeter perimeter, SiteId siteId) {
        return occupancyRepository.activeOfPersonInSite(new PersonId(perimeter.actor().id()), siteId);
    }

    /** D2, D4: the invited person chose their password: their pending invitations become active residents. */
    @Transactional
    public void activateInvitations(PersonId person) {
        Instant now = clock.instant();
        Perimeter self = Perimeter.of(Actor.person(person));
        for (SiteMembership membership : memberships.ofPerson(person)) {
            if (membership.origin() == MembershipOrigin.INVITATION && membership.activateInvitation(now)) {
                memberships.save(membership);
                giveResident(self, membership, now);
            }
        }
        occupancyRepository.activeOfPerson(person).stream()
                .filter(o -> o.validationStatus() == ValidationStatus.DECLARED)
                .forEach(o -> {
                    o.validate();
                    occupancyRepository.save(o);
                });
    }

    // ------------------------------------------------------------------------------------------- helpers

    /** Gives the default occupant role (a resident) on the site unless the person already holds it (D2, E5). */
    void giveResident(Perimeter perimeter, SiteMembership membership, Instant now) {
        Role resident = catalog.require(RoleCatalog.SEED_DEFAULT_OCCUPANT);
        if (assignments.active(membership.personId(), resident.code(), ScopeType.SITE, membership.organizationId(),
                membership.siteId()).isEmpty()) {
            roles.give(perimeter, membership.organizationId(), membership.personId(), resident,
                    ScopeType.SITE, membership.siteId().value(), now);
        }
    }

    /** Everyone who may decide on the site's requests, computed with their real permissions (K1). */
    List<PersonId> approvers(SiteSummary site) {
        Set<PersonId> candidates = assignments.activeInOrganization(site.organizationId()).stream()
                .filter(a -> a.scopeType() == ScopeType.ORGANIZATION || a.siteId().equals(Optional.of(site.id())))
                .map(RoleAssignment::personId).collect(Collectors.toSet());
        return candidates.stream()
                .filter(person -> perimeters.perimeterOf(person)
                        .can(Permission.ACCESS_APPROVE, site.organizationId(), site.id()))
                .toList();
    }

    /** An access with its person's profile. */
    MembershipView view(SiteMembership membership) {
        return new MembershipView(membership, identity.findPerson(membership.personId()).orElse(null));
    }

    /** The profiles of the people of these accesses, in one query. */
    private Map<PersonId, PersonSummary> people(List<SiteMembership> list) {
        return identity.findPersons(list.stream().map(SiteMembership::personId).collect(Collectors.toSet()))
                .stream().collect(Collectors.toMap(PersonSummary::id, Function.identity()));
    }

    /** Whether the managers may see the access: every one except a self-registration not submitted yet. */
    static boolean visibleToManagers(SiteMembership membership) {
        return membership.origin() != MembershipOrigin.SELF_REGISTRATION
                || membership.status() != MembershipStatus.PENDING || membership.submittedAt().isPresent();
    }

    /** The access when the managers may see it, or {@code NOT_FOUND}. */
    private SiteMembership loadVisible(UUID id) {
        SiteMembership membership = load(id);
        if (!visibleToManagers(membership)) {
            throw RoleAdministration.notFound();
        }
        return membership;
    }

    /** The access, or {@code NOT_FOUND}. */
    private SiteMembership load(UUID id) {
        return memberships.findById(id).orElseThrow(RoleAdministration::notFound);
    }

    /** The site, or {@code NOT_FOUND}. */
    SiteSummary site(SiteId id) {
        return sites.findSite(id).orElseThrow(RoleAdministration::notFound);
    }

    /** {@code VERSION_CONFLICT} when the access changed since it was read. */
    private static void checkVersion(SiteMembership membership, long version) {
        if (membership.version() != version) {
            throw new BusinessException("VERSION_CONFLICT", ErrorCategory.CONFLICT, "Changed in the meantime");
        }
    }
}
