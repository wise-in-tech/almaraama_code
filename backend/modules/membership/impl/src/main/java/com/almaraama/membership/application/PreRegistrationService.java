package com.almaraama.membership.application;

import com.almaraama.identity.api.Contact;
import com.almaraama.identity.api.IdentityApi;
import com.almaraama.membership.api.events.PreRegistrationCancelledV1;
import com.almaraama.membership.api.events.PreRegistrationCreatedV1;
import com.almaraama.membership.api.events.PreRegistrationKeyRenewedV1;
import com.almaraama.membership.domain.OccupancyRepository;
import com.almaraama.membership.domain.OccupancyRole;
import com.almaraama.membership.domain.PlannedOccupancy;
import com.almaraama.membership.domain.PreRegistration;
import com.almaraama.membership.domain.PreRegistrationOrigin;
import com.almaraama.membership.domain.PreRegistrationRepository;
import com.almaraama.membership.domain.PreRegistrationStatus;
import com.almaraama.membership.domain.SiteMembershipRepository;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.SiteSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;
import java.util.UUID;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Pre-registrations (D3, criterion 6, ADR-034): a manager or the promoteur creates the profile of an expected person
 * with the units planned for them; the personal key is shown once and given "by the means of their choice". No
 * account is created and no e-mail is sent here: the person registers with the site's QR code and the key.
 */
@Service
public class PreRegistrationService {

    private static final Logger LOG = LoggerFactory.getLogger(PreRegistrationService.class);

    /** The expected person and their planned units, as typed or imported. */
    public record Draft(PeopleService.PersonInput person, List<PlannedOccupancy> planned) {
    }

    /**
     * Units already planned in the same batch (resident import), so that E3 and E4 also hold between the rows of one
     * file. Empty for a single pre-registration.
     */
    static final class Batch {
        private final Set<UnitId> owners = new HashSet<>();
        private final Set<UnitId> primaryTenants = new HashSet<>();
        private final Set<String> emails = new HashSet<>();
    }

    private final PreRegistrationRepository preRegistrations;
    private final OccupancyRepository occupancyRepository;
    private final SiteMembershipRepository memberships;
    private final OccupancyBook occupancies;
    private final MembershipService membershipService;
    private final IdentityApi identity;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the ports and collaborators of the pre-registrations. */
    PreRegistrationService(PreRegistrationRepository preRegistrations, OccupancyRepository occupancyRepository,
                           SiteMembershipRepository memberships, OccupancyBook occupancies,
                           MembershipService membershipService, IdentityApi identity,
                           ApplicationEventPublisher events, Clock clock) {
        this.preRegistrations = preRegistrations;
        this.occupancyRepository = occupancyRepository;
        this.memberships = memberships;
        this.occupancies = occupancies;
        this.membershipService = membershipService;
        this.identity = identity;
        this.events = events;
        this.clock = clock;
    }

    /**
     * D3: creates a pre-registration and returns it with its key, shown this once; needs {@code
     * pre-registration:create} and {@code occupancy:create} on the site. Errors: see {@link #check}. Publishes
     * {@code PreRegistrationCreatedV1}.
     */
    @Transactional
    public PreRegistration.Issued create(Perimeter perimeter, SiteId siteId, Draft draft, int validDays) {
        SiteSummary site = requireCreate(perimeter, siteId);
        Instant now = clock.instant();
        PreRegistration.Issued issued = check(site, draft, validDays, PreRegistrationOrigin.MANUAL,
                RoleAdministration.actor(perimeter), now, new Batch());
        save(perimeter, issued, now);
        return issued;
    }

    /** Pre-registrations of a site, newest first, by status and by name or e-mail; needs {@code pre-registration:read}. */
    @Transactional(readOnly = true)
    public PageResult<PreRegistration> list(Perimeter perimeter, SiteId siteId, PreRegistrationStatus status,
                                            String q, PageQuery page) {
        SiteSummary site = membershipService.site(siteId);
        perimeter.require(Permission.PRE_REGISTRATION_READ, site.organizationId(), siteId);
        Instant now = clock.instant();
        String filter = q == null ? "" : q.strip().toLowerCase(Locale.ROOT);
        List<PreRegistration> found = preRegistrations.ofSite(siteId).stream()
                .filter(p -> status == null || p.status(now) == status)
                .filter(p -> filter.isEmpty() || p.email().contains(filter)
                        || (p.firstName() + " " + p.lastName()).toLowerCase(Locale.ROOT).contains(filter))
                .toList();
        int from = (int) Math.min(page.offset(), found.size());
        int to = Math.min(from + page.size(), found.size());
        return new PageResult<>(found.subList(from, to), page.page(), page.size(), found.size());
    }

    /** One pre-registration, never its key; needs {@code pre-registration:read} on its site. */
    @Transactional(readOnly = true)
    public PreRegistration get(Perimeter perimeter, UUID id) {
        PreRegistration pre = load(id);
        perimeter.require(Permission.PRE_REGISTRATION_READ, pre.organizationId(), pre.siteId());
        return pre;
    }

    /**
     * D3: a new key for a lost one; the old key stops working. Needs {@code pre-registration:create}; {@code
     * VERSION_CONFLICT}, {@code PRE_REGISTRATION_NOT_PENDING}. Publishes {@code PreRegistrationKeyRenewedV1}.
     */
    @Transactional
    public PreRegistration.Issued renewKey(Perimeter perimeter, UUID id, int validDays, long version) {
        PreRegistration pre = load(id);
        perimeter.require(Permission.PRE_REGISTRATION_CREATE, pre.organizationId(), pre.siteId());
        if (pre.version() != version) {
            throw new BusinessException("VERSION_CONFLICT", ErrorCategory.CONFLICT, "Changed in the meantime");
        }
        Instant now = clock.instant();
        String key = pre.renewKey(validDays, now);
        preRegistrations.save(pre);
        events.publishEvent(new PreRegistrationKeyRenewedV1(pre.id(), pre.organizationId(), pre.siteId(),
                perimeter.actor(), now));
        LOG.debug("Key of pre-registration {} renewed", pre.id());
        return new PreRegistration.Issued(pre, key);
    }

    /**
     * Cancels a pending pre-registration; its key stops working (M2: kept). Needs {@code pre-registration:cancel};
     * {@code PRE_REGISTRATION_NOT_PENDING}. Publishes {@code PreRegistrationCancelledV1}.
     */
    @Transactional
    public PreRegistration cancel(Perimeter perimeter, UUID id, String reason) {
        PreRegistration pre = load(id);
        perimeter.require(Permission.PRE_REGISTRATION_CANCEL, pre.organizationId(), pre.siteId());
        Instant now = clock.instant();
        pre.cancel(RoleAdministration.actor(perimeter), reason, now);
        preRegistrations.save(pre);
        events.publishEvent(new PreRegistrationCancelledV1(pre.id(), pre.organizationId(), pre.siteId(),
                perimeter.actor(), now));
        return pre;
    }

    // ------------------------------------------------------------------------------------- shared with import

    /** The site, after checking {@code pre-registration:create} and {@code occupancy:create} on it. */
    SiteSummary requireCreate(Perimeter perimeter, SiteId siteId) {
        SiteSummary site = membershipService.site(siteId);
        perimeter.require(Permission.PRE_REGISTRATION_CREATE, site.organizationId(), siteId);
        perimeter.require(Permission.OCCUPANCY_CREATE, site.organizationId(), siteId);
        return site;
    }

    /**
     * Checks a draft and builds the pre-registration with its key, without saving it. Errors: {@code EMAIL_INVALID},
     * {@code PHONE_INVALID}, {@code PRE_REGISTRATION_NAME_INVALID}, {@code PRE_REGISTRATION_UNITS_INVALID}, {@code
     * PRE_REGISTRATION_VALIDITY_INVALID}, {@code UNIT_NOT_IN_SITE}, {@code MEMBERSHIP_EXISTS} (the e-mail already has
     * an access to the site), {@code PRE_REGISTRATION_EXISTS} (one pending per e-mail and site), {@code
     * OCCUPANCY_OWNER_EXISTS} (E4: an active owner, or one planned elsewhere), {@code
     * OCCUPANCY_PRIMARY_TENANT_EXISTS} (E3). Reads only: safe to call for each row of an import.
     */
    PreRegistration.Issued check(SiteSummary site, Draft draft, int validDays, PreRegistrationOrigin origin,
                                 PersonId by, Instant now, Batch batch) {
        Contact contact = identity.checkContact(draft.person().email(), draft.person().phone());
        for (PlannedOccupancy planned : draft.planned()) {
            occupancies.unitOf(site.id(), planned.unitId());
        }
        identity.findPersonByEmail(contact.email())
                .flatMap(person -> memberships.current(person.id(), site.id()))
                .ifPresent(existing -> {
                    throw conflict("MEMBERSHIP_EXISTS");
                });
        List<PreRegistration> pending = preRegistrations.pendingOfSite(site.id()).stream()
                .filter(p -> p.status(now) == PreRegistrationStatus.PENDING).toList();
        if (batch.emails.contains(contact.email()) || pending.stream().anyMatch(p -> p.email().equals(contact.email()))) {
            throw conflict("PRE_REGISTRATION_EXISTS");
        }
        for (PlannedOccupancy planned : draft.planned()) {
            UnitId unit = planned.unitId();
            if (planned.role() == OccupancyRole.OWNER && (occupancyRepository.activeOwner(unit).isPresent()
                    || batch.owners.contains(unit) || plannedElsewhere(pending, unit, true))) {
                throw conflict("OCCUPANCY_OWNER_EXISTS");
            }
            if (planned.primaryTenant() && (occupancyRepository.activePrimaryTenant(unit).isPresent()
                    || batch.primaryTenants.contains(unit) || plannedElsewhere(pending, unit, false))) {
                throw conflict("OCCUPANCY_PRIMARY_TENANT_EXISTS");
            }
        }
        PeopleService.PersonInput person = draft.person();
        PreRegistration.Issued issued = PreRegistration.create(site.organizationId(), site.id(), person.firstName(),
                person.lastName(), contact.email(), contact.phone(), person.language(), draft.planned(), validDays,
                origin, by, now);
        batch.emails.add(contact.email());
        for (PlannedOccupancy planned : draft.planned()) {
            if (planned.role() == OccupancyRole.OWNER) {
                batch.owners.add(planned.unitId());
            }
            if (planned.primaryTenant()) {
                batch.primaryTenants.add(planned.unitId());
            }
        }
        return issued;
    }

    /** Saves a checked pre-registration and publishes {@code PreRegistrationCreatedV1}. */
    void save(Perimeter perimeter, PreRegistration.Issued issued, Instant now) {
        PreRegistration pre = preRegistrations.save(issued.preRegistration());
        events.publishEvent(new PreRegistrationCreatedV1(pre.id(), pre.organizationId(), pre.siteId(),
                pre.origin().name(), pre.plannedOccupancies().size(), perimeter.actor(), now));
        LOG.debug("Pre-registration {} created on site {} with {} units ({})", pre.id(), pre.siteId().value(),
                pre.plannedOccupancies().size(), pre.origin());
    }

    /** Whether another pending pre-registration plans this unit for an owner ({@code owner}) or a primary tenant. */
    private static boolean plannedElsewhere(List<PreRegistration> pending, UnitId unit, boolean owner) {
        return pending.stream().flatMap(p -> p.plannedOccupancies().stream())
                .anyMatch(p -> p.unitId().equals(unit) && (owner ? p.role() == OccupancyRole.OWNER : p.primaryTenant()));
    }

    /** The pre-registration, or {@code NOT_FOUND}. */
    private PreRegistration load(UUID id) {
        return preRegistrations.findById(id).orElseThrow(RoleAdministration::notFound);
    }

    /** A conflict with an existing access, pre-registration or occupancy. */
    private static BusinessException conflict(String code) {
        return new BusinessException(code, ErrorCategory.CONFLICT, code);
    }
}
