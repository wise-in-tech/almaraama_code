package com.almaraama.membership.application;

import com.almaraama.identity.api.IdentityApi;
import com.almaraama.identity.api.PersonSummary;
import com.almaraama.membership.api.events.OccupancyEndedV1;
import com.almaraama.membership.domain.MembershipStatus;
import com.almaraama.membership.domain.Occupancy;
import com.almaraama.membership.domain.OccupancyRepository;
import com.almaraama.membership.domain.SiteMembershipRepository;
import com.almaraama.membership.domain.ValidationStatus;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.SitesApi;
import com.almaraama.sites.api.UnitSummary;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Occupants of units (E1 to E5): added and ended by managers, never deleted (E2, M2). */
@Service
public class OccupancyService {

    /** An occupancy with its occupant's profile. */
    public record OccupancyView(Occupancy occupancy, PersonSummary person) {
    }

    private final OccupancyRepository occupancies;
    private final SiteMembershipRepository memberships;
    private final OccupancyBook book;
    private final SitesApi sites;
    private final IdentityApi identity;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /** Wires the ports and collaborators of the occupancy use cases. */
    OccupancyService(OccupancyRepository occupancies, SiteMembershipRepository memberships, OccupancyBook book,
                     SitesApi sites, IdentityApi identity, ApplicationEventPublisher events, Clock clock) {
        this.occupancies = occupancies;
        this.memberships = memberships;
        this.book = book;
        this.sites = sites;
        this.identity = identity;
        this.events = events;
        this.clock = clock;
    }

    /** Occupants of a unit, current ones first; needs {@code occupancy:read}. */
    @Transactional(readOnly = true)
    public List<OccupancyView> ofUnit(Perimeter perimeter, UnitId unitId, boolean includeEnded) {
        UnitSummary unit = unit(unitId);
        perimeter.require(Permission.OCCUPANCY_READ, unit.organizationId(), unit.siteId());
        List<Occupancy> found = occupancies.ofUnit(unitId, includeEnded);
        Map<PersonId, PersonSummary> people = identity.findPersons(found.stream().map(Occupancy::personId)
                .collect(Collectors.toSet())).stream().collect(Collectors.toMap(PersonSummary::id,
                Function.identity()));
        return found.stream().map(o -> new OccupancyView(o, people.get(o.personId()))).toList();
    }

    /**
     * Adds an occupant; needs {@code occupancy:create}; {@code PERSON_NOT_MEMBER_OF_SITE} without a pending or active
     * access.
     */
    @Transactional
    public OccupancyView start(Perimeter perimeter, UnitId unitId, PersonId person, OccupancyRequest request) {
        UnitSummary unit = unit(unitId);
        perimeter.require(Permission.OCCUPANCY_CREATE, unit.organizationId(), unit.siteId());
        MembershipStatus status = memberships.current(person, unit.siteId())
                .map(m -> m.status())
                .filter(s -> s == MembershipStatus.ACTIVE || s == MembershipStatus.PENDING)
                .orElseThrow(() -> new BusinessException("PERSON_NOT_MEMBER_OF_SITE", ErrorCategory.RULE_VIOLATION,
                        "Give the person an access to the site first"));
        Occupancy occupancy = book.start(person, unit.organizationId(), unit.siteId(), request,
                status == MembershipStatus.ACTIVE ? ValidationStatus.VALIDATED : ValidationStatus.DECLARED,
                RoleAdministration.actor(perimeter), clock.instant());
        return new OccupancyView(occupancy, identity.findPerson(person).orElse(null));
    }

    /** Ends an occupancy (E2); needs {@code occupancy:create}. Publishes {@code OccupancyEndedV1}. */
    @Transactional
    public OccupancyView end(Perimeter perimeter, UUID occupancyId, LocalDate endedOn, String reason) {
        Occupancy occupancy = occupancies.findById(occupancyId).orElseThrow(RoleAdministration::notFound);
        perimeter.require(Permission.OCCUPANCY_END, occupancy.organizationId(), occupancy.siteId());
        Instant now = clock.instant();
        occupancy.end(endedOn, reason);
        occupancies.save(occupancy);
        events.publishEvent(new OccupancyEndedV1(occupancy.id(), occupancy.organizationId(), occupancy.siteId(),
                occupancy.unitId(), occupancy.personId(), occupancy.role().name(), now));
        return new OccupancyView(occupancy, identity.findPerson(occupancy.personId()).orElse(null));
    }

    /** The unit, or {@code NOT_FOUND}. */
    private UnitSummary unit(UnitId unitId) {
        return sites.findUnit(unitId).orElseThrow(RoleAdministration::notFound);
    }
}
