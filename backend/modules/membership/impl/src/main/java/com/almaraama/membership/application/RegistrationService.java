package com.almaraama.membership.application;

import com.almaraama.identity.api.IdentityApi;
import com.almaraama.identity.api.PersonSummary;
import com.almaraama.identity.api.RegisterSelf;
import com.almaraama.membership.api.events.MembershipApprovedV1;
import com.almaraama.membership.api.events.MembershipAutoApprovedV1;
import com.almaraama.membership.api.events.MembershipRequestedV1;
import com.almaraama.membership.api.events.PreRegistrationUsedV1;
import com.almaraama.membership.domain.KeyOutcome;
import com.almaraama.membership.domain.MembershipOrigin;
import com.almaraama.membership.domain.OccupancyRepository;
import com.almaraama.membership.domain.OccupancyRole;
import com.almaraama.membership.domain.PlannedOccupancy;
import com.almaraama.membership.domain.PreRegistration;
import com.almaraama.membership.domain.PreRegistrationRepository;
import com.almaraama.membership.domain.SecretCodes;
import com.almaraama.membership.domain.SiteMembership;
import com.almaraama.membership.domain.SiteMembershipRepository;
import com.almaraama.membership.domain.ValidationStatus;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.i18n.Language;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.SiteSummary;
import com.almaraama.sites.api.SitesApi;
import com.almaraama.sites.api.UnitSummary;
import java.time.Clock;
import java.time.Instant;
import java.util.List;
import java.util.Optional;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Registration by a site's QR code (D2, D3, D6, K1, ADR-034). A new person fills in the public form: the account is
 * created by the identity module and the request waits, unseen, until the e-mail is verified. A signed-in person asks
 * for one more site. When a request is submitted, a typed pre-registration key is checked: with the matching e-mail,
 * the request is approved at once with the planned units (D3); otherwise it waits for a manager, who is notified (K1).
 * A wrong key is never reported to the person. Neither the code, the key nor the comment is ever logged.
 */
@Service
public class RegistrationService {

    private static final Logger LOG = LoggerFactory.getLogger(RegistrationService.class);

    /**
     * What the public form sends (D2). {@code key} is the optional pre-registration key (D3).
     */
    public record Registration(String code, String firstName, String lastName, String email, String phone,
                               Language language, String comment, String password, String key) {

        /** Never prints the password, the key, the e-mail nor the comment. */
        @Override
        public String toString() {
            return "Registration[language=" + language + "]";
        }
    }

    private final SiteMembershipRepository memberships;
    private final PreRegistrationRepository preRegistrations;
    private final OccupancyRepository occupancyRepository;
    private final OccupancyBook occupancies;
    private final MembershipService membershipService;
    private final RegistrationQrService codes;
    private final IdentityApi identity;
    private final SitesApi sites;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the ports and collaborators of the registration flow. */
    RegistrationService(SiteMembershipRepository memberships, PreRegistrationRepository preRegistrations,
                        OccupancyRepository occupancyRepository, OccupancyBook occupancies,
                        MembershipService membershipService, RegistrationQrService codes, IdentityApi identity,
                        SitesApi sites, ApplicationEventPublisher events, Clock clock) {
        this.memberships = memberships;
        this.preRegistrations = preRegistrations;
        this.occupancyRepository = occupancyRepository;
        this.occupancies = occupancies;
        this.membershipService = membershipService;
        this.codes = codes;
        this.identity = identity;
        this.sites = sites;
        this.events = events;
        this.clock = clock;
    }

    /**
     * D2: the public form. Checks the code first ({@code REGISTRATION_CODE_INVALID}) and the comment ({@code
     * COMMENT_TOO_LONG}), then lets the identity module check the password, e-mail and phone and create the account
     * ({@code PASSWORD_*}, {@code EMAIL_INVALID}, {@code PHONE_INVALID}). For a new account, records a pending
     * request, not submitted until the e-mail is verified. For a known e-mail, records nothing (the identity module
     * sends the "you already have an account" e-mail). The caller answers the same in both cases.
     */
    @Transactional
    public void register(Registration form) {
        SiteSummary site = codes.resolve(form.code());
        Instant now = clock.instant();
        String keyHash = keyHash(form.key());
        SiteMembership.checkComment(form.comment());
        Optional<PersonId> created = identity.registerSelf(new RegisterSelf(form.firstName(), form.lastName(),
                form.email(), form.phone(), form.language(), form.password()));
        created.ifPresent(person -> {
            SiteMembership membership = memberships.save(SiteMembership.requested(person, site.organizationId(),
                    site.id(), form.comment(), keyHash, false, now));
            LOG.debug("Registration {} recorded on site {}, waiting for the e-mail verification", membership.id(),
                    site.id().value());
        });
    }

    /**
     * D2 step 5, D6: a signed-in person asks for one more site with its code, a comment and an optional key. The
     * request is submitted at once (the e-mail is already verified). {@code REGISTRATION_CODE_INVALID}, {@code
     * MEMBERSHIP_EXISTS}, {@code COMMENT_TOO_LONG}.
     */
    @Transactional
    public SiteMembership requestAccess(Perimeter perimeter, String code, String comment, String key) {
        SiteSummary site = codes.resolve(code);
        PersonId person = new PersonId(perimeter.actor().id());
        if (memberships.current(person, site.id()).isPresent()) {
            throw new BusinessException("MEMBERSHIP_EXISTS", ErrorCategory.CONFLICT, "Already has an access");
        }
        Instant now = clock.instant();
        SiteMembership membership = memberships.save(SiteMembership.requested(person, site.organizationId(),
                site.id(), comment, keyHash(key), true, now));
        submitted(membership, site, now);
        return membership;
    }

    /**
     * ADR-034: the person verified their e-mail; their self-registrations waiting for it reach the managers now (or
     * are approved by their key). Called by the listener of {@code EmailVerifiedV1}.
     */
    @Transactional
    public void emailVerified(PersonId person) {
        Instant now = clock.instant();
        for (SiteMembership membership : memberships.ofPerson(person)) {
            if (membership.origin() == MembershipOrigin.SELF_REGISTRATION && membership.submit(now)) {
                memberships.save(membership);
                submitted(membership, membershipService.site(membership.siteId()), now);
            }
        }
    }

    /**
     * A request has just been submitted: checks its key, then either approves it with the planned units (D3), or
     * leaves it pending and notifies the people who may decide (K1).
     */
    private void submitted(SiteMembership membership, SiteSummary site, Instant now) {
        Optional<String> keyHash = membership.keyHash();
        if (keyHash.isEmpty()) {
            requestReachesManagers(membership, site, now);
            return;
        }
        String email = identity.findPerson(membership.personId()).map(PersonSummary::email).orElse("");
        Optional<PreRegistration> matching = preRegistrations.byKeyHash(keyHash.get())
                .filter(pre -> pre.matches(site.id(), email, now));
        if (matching.isEmpty()) {
            membership.keyChecked(KeyOutcome.NOT_MATCHED, null, now);
            memberships.save(membership);
            LOG.warn("Pre-registration key not matched for access {} on site {}", membership.id(),
                    site.id().value());
            requestReachesManagers(membership, site, now);
            return;
        }
        PreRegistration pre = matching.get();
        if (!allFree(membership.personId(), site, pre.plannedOccupancies())) {
            membership.keyChecked(KeyOutcome.MATCHED_NEEDS_REVIEW, pre.id(), now);
            memberships.save(membership);
            pre.use(membership.personId(), membership.id(), now);
            preRegistrations.save(pre);
            events.publishEvent(new PreRegistrationUsedV1(pre.id(), site.organizationId(), site.id(),
                    membership.personId(), membership.id(), false, now));
            LOG.debug("Key of pre-registration {} matched for access {}, but a planned unit is taken: manual review",
                    pre.id(), membership.id());
            requestReachesManagers(membership, site, now);
            return;
        }
        autoApprove(membership, site, pre, now);
    }

    /**
     * D3: approves the request in the name of the person who pre-registered it: buildings of the planned units (C7),
     * the RESIDENT role, the planned occupancies; the key is used.
     */
    private void autoApprove(SiteMembership membership, SiteSummary site, PreRegistration pre, Instant now) {
        List<OccupancyRequest> requests = pre.plannedOccupancies().stream()
                .map(p -> new OccupancyRequest(p.unitId(), p.role(), p.primaryTenant(), null)).toList();
        PersonId by = pre.createdBy();
        membership.keyChecked(KeyOutcome.MATCHED, pre.id(), now);
        membership.approveWithKey(occupancies.buildingsOfUnits(site.id(), requests), by, now);
        memberships.save(membership);
        Perimeter onBehalf = Perimeter.of(Actor.person(by));
        membershipService.giveResident(onBehalf, membership, now);
        for (OccupancyRequest request : requests) {
            occupancies.start(membership.personId(), site.organizationId(), site.id(), request,
                    ValidationStatus.VALIDATED, by, now);
        }
        pre.use(membership.personId(), membership.id(), now);
        preRegistrations.save(pre);
        String name = personName(membership.personId());
        events.publishEvent(new MembershipApprovedV1(membership.id(), site.organizationId(), site.id(),
                membership.personId(), site.name(), Actor.person(by), now));
        events.publishEvent(new MembershipAutoApprovedV1(membership.id(), site.organizationId(), site.id(),
                membership.personId(), name, site.name(), membershipService.approvers(site), now));
        events.publishEvent(new PreRegistrationUsedV1(pre.id(), site.organizationId(), site.id(),
                membership.personId(), membership.id(), true, now));
        LOG.debug("Access {} approved automatically by pre-registration {} with {} units", membership.id(), pre.id(),
                requests.size());
    }

    /** K1: the request waits; everyone who may decide on the site is notified. */
    private void requestReachesManagers(SiteMembership membership, SiteSummary site, Instant now) {
        events.publishEvent(new MembershipRequestedV1(membership.id(), site.organizationId(), site.id(),
                membership.personId(), personName(membership.personId()), site.name(),
                membershipService.approvers(site), now));
        LOG.debug("Access request {} submitted on site {}", membership.id(), site.id().value());
    }

    /**
     * Whether every planned unit can still be given to the person: an active unit of the site, not already occupied
     * by them, no other active owner for an owner (E4), no other primary tenant (E3).
     */
    private boolean allFree(PersonId person, SiteSummary site, List<PlannedOccupancy> planned) {
        for (PlannedOccupancy p : planned) {
            Optional<UnitSummary> unit = sites.findUnit(p.unitId())
                    .filter(u -> u.siteId().equals(site.id()) && u.active());
            if (unit.isEmpty() || occupancyRepository.active(person, p.unitId()).isPresent()
                    || p.role() == OccupancyRole.OWNER && occupancyRepository.activeOwner(p.unitId()).isPresent()
                    || p.primaryTenant() && occupancyRepository.activePrimaryTenant(p.unitId()).isPresent()) {
                return false;
            }
        }
        return true;
    }

    /** First and last name of the person, for the managers' notification. */
    private String personName(PersonId person) {
        return identity.findPerson(person).map(p -> p.firstName() + " " + p.lastName()).orElse("");
    }

    /** SHA-256 of a typed key, or {@code null} when none was typed. */
    private static String keyHash(String key) {
        return SecretCodes.normalize(key).isEmpty() ? null : SecretCodes.hash(key);
    }
}
