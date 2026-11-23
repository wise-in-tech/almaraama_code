package com.almaraama.membership.adapters.in.web;

import com.almaraama.membership.adapters.in.web.MembershipWebDtos.MyAccess;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.MyMembership;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.MyOccupancy;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.MyOrganization;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.MySite;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.SiteAccessRequest;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.SupportSessionResponse;
import com.almaraama.membership.application.MembershipService;
import com.almaraama.membership.application.RegistrationService;
import com.almaraama.membership.application.SupportSessionService;
import com.almaraama.membership.domain.Occupancy;
import com.almaraama.membership.domain.SiteMembership;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.OrganizationSummary;
import com.almaraama.sites.api.SiteSummary;
import com.almaraama.sites.api.SitesApi;
import com.almaraama.sites.api.UnitSummary;
import jakarta.validation.Valid;
import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * What the signed-in person can see and do, their site accesses, and requests for more sites (D2, D6). Menus are
 * built from {@code /v1/me/access}, but the server still checks every request.
 */
@RestController
class MyAccessController {

    private final MembershipService memberships;
    private final RegistrationService registrations;
    private final SupportSessionService supportSessions;
    private final SupportSessionController supportViews;
    private final SitesApi sites;
    private final Names names;

    /** Wires the caller's own views. */
    MyAccessController(MembershipService memberships, RegistrationService registrations,
                       SupportSessionService supportSessions, SupportSessionController supportViews, SitesApi sites,
                       Names names) {
        this.memberships = memberships;
        this.registrations = registrations;
        this.supportSessions = supportSessions;
        this.supportViews = supportViews;
        this.sites = sites;
        this.names = names;
    }

    /**
     * {@code GET /v1/me/access}: organizations and sites of the perimeter with roles and effective permissions, and
     * the caller's open support session (ADR-035).
     */
    @GetMapping("/v1/me/access")
    MyAccess access(Perimeter perimeter) {
        Set<OrganizationId> organizationIds = new HashSet<>(perimeter.organizations().keySet());
        perimeter.sites().values().forEach(s -> organizationIds.add(s.organizationId()));
        Map<UUID, OrganizationSummary> organizations = names.organizations(organizationIds);
        Map<UUID, SiteSummary> siteNames = names.sites(perimeter.sites().keySet());
        List<MyOrganization> myOrganizations = perimeter.organizations().entrySet().stream()
                .filter(e -> organizations.containsKey(e.getKey().value()))
                .map(e -> {
                    OrganizationSummary o = organizations.get(e.getKey().value());
                    return new MyOrganization(o.id().value(), o.type(), o.name(),
                            e.getValue().roles().stream().map(r -> r.value()).sorted().toList(),
                            MembershipWebDtos.codes(e.getValue().permissions()));
                })
                .sorted(Comparator.comparing(MyOrganization::name)).toList();
        List<MySite> mySites = perimeter.sites().entrySet().stream()
                .filter(e -> siteNames.containsKey(e.getKey().value()))
                .map(e -> new MySite(e.getKey().value(), e.getValue().organizationId().value(),
                        siteNames.get(e.getKey().value()).name(), e.getValue().roles().stream().map(r -> r.value()).sorted().toList(),
                        MembershipWebDtos.codes(e.getValue().permissions()),
                        e.getValue().allowedBuildingIds().stream().map(b -> b.value()).sorted().toList(),
                        e.getValue().occupiedUnitIds().stream().map(UnitId::value).sorted().toList()))
                .sorted(Comparator.comparing(MySite::name)).toList();
        SupportSessionResponse support = perimeter.actor().supportSession().isPresent()
                ? supportSessions.openOf(perimeter).map(supportViews::response).orElse(null) : null;
        return new MyAccess(support, MembershipWebDtos.codes(perimeter.platformPermissions()), myOrganizations,
                mySites);
    }

    /** {@code GET /v1/me/memberships}: the caller's site accesses, pending ones included, with their units. */
    @GetMapping("/v1/me/memberships")
    List<MyMembership> mine(Perimeter perimeter) {
        return memberships.mine(perimeter).stream().map(m -> mine(perimeter, m)).toList();
    }

    /**
     * {@code POST /v1/me/access-requests}: asks for one more site with its registration code, a comment and an
     * optional pre-registration key (D2 step 5, D3, D6). Approved at once when the key matches.
     */
    @PostMapping("/v1/me/access-requests")
    @ResponseStatus(HttpStatus.CREATED)
    MyMembership request(Perimeter perimeter, @Valid @RequestBody SiteAccessRequest request) {
        return mine(perimeter, registrations.requestAccess(perimeter, request.code(), request.comment(),
                request.preRegistrationKey()));
    }

    /** One of the caller's accesses with the names of its site and organization and the caller's units there. */
    private MyMembership mine(Perimeter perimeter, SiteMembership m) {
        String siteName = sites.findSite(m.siteId()).map(SiteSummary::name).orElse("");
        String organization = sites.findOrganization(m.organizationId()).map(OrganizationSummary::name).orElse("");
        List<Occupancy> occupancies = memberships.myOccupancies(perimeter, m.siteId());
        Map<UnitId, UnitSummary> units = sites.findUnits(occupancies.stream().map(Occupancy::unitId).toList())
                .stream().collect(Collectors.toMap(UnitSummary::id, Function.identity()));
        return new MyMembership(m.id(), m.siteId().value(), siteName, organization, m.status(),
                occupancies.stream().filter(o -> units.containsKey(o.unitId()))
                        .map(o -> new MyOccupancy(o.id(), o.unitId().value(), units.get(o.unitId()).reference(),
                                units.get(o.unitId()).type(), o.role(), o.startedOn())).toList());
    }
}
