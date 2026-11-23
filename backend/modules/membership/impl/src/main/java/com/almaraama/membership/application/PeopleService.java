package com.almaraama.membership.application;

import com.almaraama.identity.api.IdentityApi;
import com.almaraama.identity.api.InvitationIssued;
import com.almaraama.identity.api.InvitePerson;
import com.almaraama.identity.api.PasswordLinkIssued;
import com.almaraama.identity.api.PersonSummary;
import com.almaraama.membership.api.events.RoleAssignedV1;
import com.almaraama.membership.domain.MembershipStatus;
import com.almaraama.membership.domain.PermissionAdjustmentRepository;
import com.almaraama.membership.domain.Role;
import com.almaraama.membership.domain.RoleAssignment;
import com.almaraama.membership.domain.RoleKind;
import com.almaraama.membership.domain.RoleAssignmentRepository;
import com.almaraama.membership.domain.SiteMembership;
import com.almaraama.membership.domain.SiteMembershipRepository;
import com.almaraama.membership.domain.ValidationStatus;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.access.RoleCode;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.i18n.Language;
import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.CreateOrganization;
import com.almaraama.sites.api.OrganizationType;
import com.almaraama.sites.api.SiteSummary;
import com.almaraama.sites.api.SitesApi;
import java.time.Clock;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Accounts of an organization (D1, D2): onboarding a client, the list of its people, invitations to the organization
 * or to a site with units. Calls the identity and sites modules in the same transaction (accepted exception to
 * CF-4, as in the UML sequence 6.1).
 */
@Service
public class PeopleService {

    private static final Logger LOG = LoggerFactory.getLogger(PeopleService.class);

    /** Who to create an account for (D1): the e-mail is the sign-in identifier, the phone is mandatory. */
    public record PersonInput(String firstName, String lastName, String email, String phone, Language language) {
    }

    /** A role to give with an invitation. */
    public record RoleSpec(RoleCode role, ScopeType scopeType, UUID scopeId) {
    }

    /** One person of the organization with their roles there. */
    public record OrganizationPerson(PersonSummary person, List<RoleSpec> roles, boolean hasAdjustments,
                                     Set<SiteId> siteIds) {
    }

    /** Result of an invitation; the "set my password" link went to the person's e-mail (ADR-031). */
    public record Invited(PersonId personId, InvitationIssued invitation) {
    }

    /** A client just onboarded and its owner's invitation. */
    public record Onboarded(OrganizationId organizationId, Invited owner) {
    }

    private final IdentityApi identity;
    private final SitesApi sites;
    private final RoleCatalog catalog;
    private final RoleAssignmentRepository assignments;
    private final PermissionAdjustmentRepository adjustments;
    private final SiteMembershipRepository memberships;
    private final RoleAdministration roles;
    private final OccupancyBook occupancies;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the ports and collaborators of onboarding, people and invitations. */
    PeopleService(IdentityApi identity, SitesApi sites, RoleCatalog catalog, RoleAssignmentRepository assignments,
                  PermissionAdjustmentRepository adjustments, SiteMembershipRepository memberships,
                  RoleAdministration roles, OccupancyBook occupancies, ApplicationEventPublisher events, Clock clock) {
        this.identity = identity;
        this.sites = sites;
        this.catalog = catalog;
        this.assignments = assignments;
        this.adjustments = adjustments;
        this.memberships = memberships;
        this.roles = roles;
        this.occupancies = occupancies;
        this.events = events;
        this.clock = clock;
    }

    // ---------------------------------------------------------------------------------------- onboarding (C8)

    /**
     * Our team onboards a client (C8, C12): the organization in its country, and its first person with the promoteur
     * role ({@code grantsAll}); needs {@code organization:create} on the platform; {@code COUNTRY_UNKNOWN}.
     */
    @Transactional
    public Onboarded onboard(Perimeter perimeter, OrganizationType type, String name, String countryCode,
                             String contactPhone, String contactEmail, PersonInput owner) {
        perimeter.requireOnPlatform(Permission.ORGANIZATION_CREATE);
        Instant now = clock.instant();
        OrganizationId organization = sites.createOrganization(new CreateOrganization(type, name, countryCode,
                contactPhone, contactEmail, perimeter.actor()));
        InvitationIssued issued = invite(perimeter, owner);
        Role role = catalog.require(RoleCatalog.SEED_ORGANIZATION_ADMIN);
        RoleAssignment assignment = assignments.save(RoleAssignment.onOrganization(issued.personId(), role,
                organization, perimeter.actor(), now));
        events.publishEvent(new RoleAssignedV1(assignment.id(), issued.personId(), role.code(),
                ScopeType.ORGANIZATION, Optional.of(organization), Optional.empty(), perimeter.actor(), now));
        LOG.debug("Organization {} onboarded, first promoteur {}", organization.value(), issued.personId().value());
        return new Onboarded(organization, new Invited(issued.personId(), issued));
    }

    /**
     * C8, C12: our team invites one more promoteur to an existing organization (for example when the first one left);
     * needs {@code organization:create} on the platform; {@code NOT_FOUND} for an unknown organization, {@code
     * ROLE_ASSIGNMENT_EXISTS} when the person already is its promoteur. Publishes {@code RoleAssignedV1}.
     */
    @Transactional
    public Invited inviteOwner(Perimeter perimeter, OrganizationId organization, PersonInput owner) {
        perimeter.requireOnPlatform(Permission.ORGANIZATION_CREATE);
        sites.findOrganization(organization).orElseThrow(RoleAdministration::notFound);
        Role role = catalog.require(RoleCatalog.SEED_ORGANIZATION_ADMIN);
        identity.findPersonByEmail(owner.email())
                .filter(existing -> assignments.active(existing.id(), role.code(), ScopeType.ORGANIZATION,
                        organization, null).isPresent())
                .ifPresent(existing -> {
                    throw new BusinessException("ROLE_ASSIGNMENT_EXISTS", ErrorCategory.CONFLICT,
                            "Already a promoteur of this organization");
                });
        InvitationIssued issued = invite(perimeter, owner);
        Instant now = clock.instant();
        RoleAssignment assignment = assignments.save(RoleAssignment.onOrganization(issued.personId(), role,
                organization, perimeter.actor(), now));
        events.publishEvent(new RoleAssignedV1(assignment.id(), issued.personId(), role.code(),
                ScopeType.ORGANIZATION, Optional.of(organization), Optional.empty(), perimeter.actor(), now));
        LOG.debug("Promoteur {} added to organization {} by our team", issued.personId().value(), organization.value());
        return new Invited(issued.personId(), issued);
    }

    // -------------------------------------------------------------------------------------------- people

    /**
     * People with a role or an access in the organization, filtered by name, e-mail or phone, ordered by last name;
     * needs {@code account:read}.
     */
    @Transactional(readOnly = true)
    public PageResult<OrganizationPerson> list(Perimeter perimeter, OrganizationId organization, String q,
                                               PageQuery page) {
        perimeter.require(Permission.ACCOUNT_READ, organization);
        Map<PersonId, List<RoleSpec>> rolesOf = new HashMap<>();
        Map<PersonId, Set<SiteId>> sitesOf = new HashMap<>();
        for (RoleAssignment a : assignments.activeInOrganization(organization)) {
            rolesOf.computeIfAbsent(a.personId(), k -> new ArrayList<>()).add(new RoleSpec(a.role(),
                    a.scopeType(), a.siteId().map(SiteId::value).orElse(organization.value())));
            a.siteId().ifPresent(site -> sitesOf.computeIfAbsent(a.personId(), k -> new HashSet<>()).add(site));
        }
        for (SiteMembership m : memberships.activeInOrganization(organization)) {
            rolesOf.computeIfAbsent(m.personId(), k -> new ArrayList<>());
            sitesOf.computeIfAbsent(m.personId(), k -> new HashSet<>()).add(m.siteId());
        }
        Set<PersonId> adjusted = new HashSet<>(adjustments.personsWithActiveAdjustments(organization));
        String filter = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        String digits = filter.replaceAll("\\D", "");
        List<OrganizationPerson> people = identity.findPersons(rolesOf.keySet()).stream()
                .filter(p -> filter.isEmpty()
                        || (p.firstName() + " " + p.lastName()).toLowerCase(Locale.ROOT).contains(filter)
                        || p.email().contains(filter)
                        || !digits.isEmpty() && p.phone().contains(digits))
                .sorted(Comparator.comparing((PersonSummary p) -> p.lastName().toLowerCase(Locale.ROOT))
                        .thenComparing(p -> p.firstName().toLowerCase(Locale.ROOT)))
                .map(p -> new OrganizationPerson(p, rolesOf.get(p.id()), adjusted.contains(p.id()),
                        sitesOf.getOrDefault(p.id(), Set.of())))
                .toList();
        int from = (int) Math.min(page.offset(), people.size());
        int to = Math.min(from + page.size(), people.size());
        return new PageResult<>(people.subList(from, to), page.page(), page.size(), people.size());
    }

    // --------------------------------------------------------------------------------------- invitations (D4)

    /**
     * Creates an account in the organization with roles (C8, D4); needs {@code account:create} on the organization
     * and, for each role, the right to give it (RB-1, {@code ROLE_NOT_GRANTABLE}).
     */
    @Transactional
    public Invited inviteToOrganization(Perimeter perimeter, OrganizationId organization, PersonInput person,
                                        List<RoleSpec> roleSpecs) {
        perimeter.require(Permission.ACCOUNT_CREATE, organization);
        List<Role> resolved = new ArrayList<>();
        for (RoleSpec spec : roleSpecs) {
            Role role = catalog.require(spec.role());
            Optional<SiteId> site = spec.scopeType() == ScopeType.SITE && spec.scopeId() != null
                    ? Optional.of(new SiteId(spec.scopeId())) : Optional.empty();
            roles.requireCanGrant(perimeter, organization, site, role);
            resolved.add(role);
        }
        InvitationIssued issued = invite(perimeter, person);
        Instant now = clock.instant();
        for (int i = 0; i < roleSpecs.size(); i++) {
            RoleSpec spec = roleSpecs.get(i);
            roles.give(perimeter, organization, issued.personId(), resolved.get(i), spec.scopeType(), spec.scopeId(),
                    now);
        }
        return new Invited(issued.personId(), issued);
    }

    /**
     * D4, E1, E5: a person created on a site with their units, usually a resident; needs {@code account:invite} on
     * the site, {@code occupancy:create} when units are given, and the right to give the role (RB-1: a manager gives
     * only roles of kind {@code OCCUPANT}). A resident's access becomes active, with the role, once they set their
     * password.
     */
    @Transactional
    public Invited inviteToSite(Perimeter perimeter, SiteId siteId, PersonInput person, Optional<RoleCode> roleCode,
                                List<OccupancyRequest> units, Optional<List<UUID>> allowedBuildings) {
        SiteSummary site = sites.findSite(siteId).orElseThrow(RoleAdministration::notFound);
        perimeter.require(Permission.ACCOUNT_INVITE, site.organizationId(), siteId);
        if (!units.isEmpty()) {
            perimeter.require(Permission.OCCUPANCY_CREATE, site.organizationId(), siteId);
        }
        Role role = catalog.require(roleCode.orElse(RoleCatalog.SEED_DEFAULT_OCCUPANT));
        roles.requireCanGrant(perimeter, site.organizationId(), Optional.of(siteId), role);
        Set<BuildingId> buildings = allowedBuildings
                .map(ids -> occupancies.buildingsOf(siteId, ids))
                .orElseGet(() -> occupancies.buildingsOfUnits(siteId, units));
        InvitationIssued issued = invite(perimeter, person);
        PersonId personId = issued.personId();
        boolean active = issued.existing();
        Instant now = clock.instant();

        if (role.kind() != RoleKind.OCCUPANT) {
            roles.give(perimeter, site.organizationId(), personId, role, ScopeType.SITE, siteId.value(), now);
        } else {
            if (memberships.current(personId, siteId).isPresent()) {
                throw new BusinessException("MEMBERSHIP_EXISTS", ErrorCategory.CONFLICT, "Already has an access");
            }
            memberships.save(SiteMembership.invited(personId, site.organizationId(), siteId, buildings, active,
                    now));
            if (active) {
                roles.give(perimeter, site.organizationId(), personId, role, ScopeType.SITE, siteId.value(), now);
            }
        }
        PersonId by = RoleAdministration.actor(perimeter);
        for (OccupancyRequest unit : units) {
            occupancies.start(personId, site.organizationId(), siteId, unit,
                    active ? ValidationStatus.VALIDATED : ValidationStatus.DECLARED, by, now);
        }
        return new Invited(personId, issued);
    }

    /**
     * Sends a person a new password link by e-mail (D4, D5): "set my password" when invited, a reset link when active.
     * Needs {@code account:invite} on the organization, or on a site of the person's access when the person holds only
     * roles of kind {@code OCCUPANT} (RB-1: a manager acts for residents only). {@code NOT_FOUND} for someone the
     * organization does not know.
     */
    @Transactional
    public PasswordLinkIssued sendPasswordLink(Perimeter perimeter, OrganizationId organization, PersonId person) {
        perimeter.requireVisible(organization);
        List<RoleAssignment> rolesThere = assignments.activeInOrganization(organization).stream()
                .filter(a -> a.personId().equals(person)).toList();
        List<SiteMembership> accesses = memberships.ofPerson(person).stream()
                .filter(m -> m.organizationId().equals(organization) && m.status() != MembershipStatus.REJECTED)
                .toList();
        if (rolesThere.isEmpty() && accesses.isEmpty()) {
            throw RoleAdministration.notFound();
        }
        boolean onlyOccupant = rolesThere.stream().allMatch(a -> catalog.isKind(a.role(), RoleKind.OCCUPANT));
        boolean allowed = perimeter.can(Permission.ACCOUNT_INVITE, organization)
                || onlyOccupant && accesses.stream()
                .anyMatch(m -> perimeter.can(Permission.ACCOUNT_INVITE, organization, m.siteId()));
        if (!allowed) {
            throw new BusinessException("PERMISSION_DENIED", ErrorCategory.PERMISSION_DENIED, "Cannot send a link");
        }
        LOG.debug("Password link for person {} requested by {}", person.value(), perimeter.actor().id());
        return identity.sendPasswordLink(person, perimeter.actor());
    }

    /** Creates or finds the account through the identity module, the caller being the issuer. */
    private InvitationIssued invite(Perimeter perimeter, PersonInput person) {
        return identity.invitePerson(new InvitePerson(person.firstName(), person.lastName(), person.email(),
                person.phone(), person.language(), perimeter.actor()));
    }
}
