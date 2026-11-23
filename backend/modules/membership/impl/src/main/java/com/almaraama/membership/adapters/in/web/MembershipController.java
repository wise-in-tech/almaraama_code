package com.almaraama.membership.adapters.in.web;

import com.almaraama.membership.adapters.in.web.MembershipWebDtos.AllowedBuildingsRequest;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.ApproveMembershipRequest;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.EndOccupancyRequest;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.OccupancyInput;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.OccupancyResponse;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.Page;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.PersonRef;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.ReasonRequest;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.SiteMembershipResponse;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.StartOccupancyRequest;
import com.almaraama.membership.application.MembershipService;
import com.almaraama.membership.application.MembershipService.MembershipView;
import com.almaraama.membership.application.OccupancyService;
import com.almaraama.membership.application.OccupancyRequest;
import com.almaraama.membership.application.OccupancyService.OccupancyView;
import com.almaraama.membership.domain.MembershipStatus;
import com.almaraama.membership.domain.SiteMembership;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Site accesses and their decisions, and occupancies (D2, C7, E1 to E5, K1). */
@RestController
class MembershipController {

    private final MembershipService memberships;
    private final OccupancyService occupancies;
    private final Names names;

    /** Wires the use cases of site accesses and occupancies. */
    MembershipController(MembershipService memberships, OccupancyService occupancies, Names names) {
        this.memberships = memberships;
        this.occupancies = occupancies;
        this.names = names;
    }

    // ------------------------------------------------------------------------------------------ memberships

    /**
     * {@code GET /v1/sites/{id}/memberships}: accesses of the site; needs {@code account:read}; {@code status=PENDING}
     * for requests (P1-3).
     */
    @GetMapping("/v1/sites/{siteId}/memberships")
    Page<SiteMembershipResponse> ofSite(Perimeter perimeter, @PathVariable UUID siteId,
                                        @RequestParam(required = false) MembershipStatus status,
                                        @RequestParam(required = false) String q,
                                        @RequestParam(required = false) Integer page,
                                        @RequestParam(required = false) Integer size) {
        PageResult<MembershipView> found = memberships.ofSite(perimeter, new SiteId(siteId), status,
                PeopleController.shorten(q), PageQuery.of(page, size));
        Map<UUID, PersonRef> deciders = names.people(found.items().stream()
                .map(v -> v.membership().decidedBy().map(PersonId::value).orElse(null))
                .filter(Objects::nonNull).toList());
        return Page.of(found, v -> response(v, deciders));
    }

    /** {@code POST /v1/site-memberships/{id}/approve}: needs {@code access:approve} (D2 step 3, E5). */
    @PostMapping("/v1/site-memberships/{membershipId}/approve")
    SiteMembershipResponse approve(Perimeter perimeter, @PathVariable UUID membershipId,
                                   @Valid @RequestBody ApproveMembershipRequest request) {
        return response(memberships.approve(perimeter, membershipId, request.allowedBuildingIds(),
                request.occupancies().stream().map(MembershipController::request).toList(), request.version()));
    }

    /** {@code POST /v1/site-memberships/{id}/reject}: needs {@code access:approve}. */
    @PostMapping("/v1/site-memberships/{membershipId}/reject")
    SiteMembershipResponse reject(Perimeter perimeter, @PathVariable UUID membershipId,
                                  @Valid @RequestBody ReasonRequest request) {
        return response(memberships.reject(perimeter, membershipId, request.reason()));
    }

    /** {@code PUT /v1/site-memberships/{id}/allowed-buildings}: needs {@code access:restrict} (C7). */
    @PutMapping("/v1/site-memberships/{membershipId}/allowed-buildings")
    SiteMembershipResponse restrict(Perimeter perimeter, @PathVariable UUID membershipId,
                                    @Valid @RequestBody AllowedBuildingsRequest request) {
        return response(memberships.restrict(perimeter, membershipId, request.buildingIds(), request.version()));
    }

    /** {@code POST /v1/site-memberships/{id}/deactivate}: needs {@code access:revoke} (D6). */
    @PostMapping("/v1/site-memberships/{membershipId}/deactivate")
    SiteMembershipResponse deactivate(Perimeter perimeter, @PathVariable UUID membershipId,
                                      @Valid @RequestBody ReasonRequest request) {
        return response(memberships.deactivate(perimeter, membershipId, request.reason()));
    }

    /** {@code POST /v1/site-memberships/{id}/reactivate}: needs {@code access:approve}. */
    @PostMapping("/v1/site-memberships/{membershipId}/reactivate")
    SiteMembershipResponse reactivate(Perimeter perimeter, @PathVariable UUID membershipId) {
        return response(memberships.reactivate(perimeter, membershipId));
    }

    // ------------------------------------------------------------------------------------------ occupancies

    /** {@code GET /v1/units/{id}/occupancies}: needs {@code account:read}. */
    @GetMapping("/v1/units/{unitId}/occupancies")
    List<OccupancyResponse> occupancies(Perimeter perimeter, @PathVariable UUID unitId,
                                        @RequestParam(defaultValue = "false") boolean includeEnded) {
        return occupancies.ofUnit(perimeter, new UnitId(unitId), includeEnded).stream()
                .map(MembershipController::response).toList();
    }

    /**
     * {@code POST /v1/units/{id}/occupancies}: needs {@code occupancy:create} (E1 to E5); the unit comes from the path.
     */
    @PostMapping("/v1/units/{unitId}/occupancies")
    @ResponseStatus(HttpStatus.CREATED)
    OccupancyResponse startOccupancy(Perimeter perimeter, @PathVariable UUID unitId,
                                     @Valid @RequestBody StartOccupancyRequest request) {
        return response(occupancies.start(perimeter, new UnitId(unitId), new PersonId(request.personId()),
                request(new OccupancyInput(unitId, request.role(), request.primaryTenant(), request.startedOn()))));
    }

    /** {@code POST /v1/occupancies/{id}/end}: needs {@code occupancy:end} (E2). */
    @PostMapping("/v1/occupancies/{occupancyId}/end")
    OccupancyResponse endOccupancy(Perimeter perimeter, @PathVariable UUID occupancyId,
                                   @Valid @RequestBody EndOccupancyRequest request) {
        return response(occupancies.end(perimeter, occupancyId, request.endedOn(), request.reason()));
    }

    // ------------------------------------------------------------------------------------------- mapping

    /** An occupancy request from its body; not primary tenant unless said. */
    static OccupancyRequest request(OccupancyInput input) {
        return new OccupancyRequest(new UnitId(input.unitId()), input.role(),
                Boolean.TRUE.equals(input.primaryTenant()), input.startedOn());
    }

    /** Response body of one access, with the decider's name. */
    private SiteMembershipResponse response(MembershipView view) {
        Map<UUID, PersonRef> deciders = names.people(view.membership().decidedBy()
                .map(p -> List.of(p.value())).orElse(List.of()));
        return response(view, deciders);
    }

    /** Response body of one access, with deciders already resolved for a whole page. */
    private static SiteMembershipResponse response(MembershipView view, Map<UUID, PersonRef> deciders) {
        SiteMembership m = view.membership();
        return new SiteMembershipResponse(m.id(), m.siteId().value(), Names.ref(view.person()),
                view.person() == null ? null : view.person().phone(), m.status(), m.origin(),
                m.allowedBuildingIds().stream().map(b -> b.value()).sorted().toList(),
                m.comment().orElse(null), m.keyOutcome(), m.preRegistrationId().orElse(null),
                m.submittedAt().orElse(null),
                m.decidedBy().map(p -> deciders.get(p.value())).orElse(null), m.decidedAt().orElse(null),
                m.decisionReason().orElse(null), m.createdAt(), m.endedAt().orElse(null), m.version());
    }

    /** Response body of an occupancy with the occupant's name. */
    private static OccupancyResponse response(OccupancyView view) {
        var o = view.occupancy();
        return new OccupancyResponse(o.id(), o.unitId().value(), o.siteId().value(), Names.ref(view.person()),
                o.role(), o.primaryTenant(), o.startedOn(), o.endedOn().orElse(null), o.endReason().orElse(null));
    }
}
