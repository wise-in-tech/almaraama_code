package com.almaraama.membership.adapters.in.web;

import com.almaraama.identity.api.InvitationIssued;
import com.almaraama.identity.api.PasswordLinkIssued;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.CreateOrganizationRequest;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.InvitationResult;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.OccupancyInput;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.OrganizationCreated;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.OrganizationInvitationRequest;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.OrganizationPersonResponse;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.OrganizationResponse;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.PasswordLinkSent;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.Page;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.PersonInput;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.RoleAssignmentSpec;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.Scope;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.SiteInvitationRequest;
import com.almaraama.membership.application.PeopleService;
import com.almaraama.membership.application.PeopleService.Invited;
import com.almaraama.membership.application.PeopleService.Onboarded;
import com.almaraama.membership.application.PeopleService.RoleSpec;
import com.almaraama.shared.access.RoleCode;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.SitesApi;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/** Onboarding, the people of an organization and invitations (D1, D2, C8). */
@RestController
class PeopleController {

    private final PeopleService people;
    private final SitesApi sites;

    /**
     * @param people onboarding, people, invitations and password links
     * @param sites  reads the organization just created
     */
    PeopleController(PeopleService people, SitesApi sites) {
        this.people = people;
        this.sites = sites;
    }

    /**
     * {@code POST /v1/organizations} (C8, C12): our team onboards a client in its country and its first promoteur, who
     * receives the "set my password" link by e-mail; needs {@code organization:create}.
     */
    @PostMapping("/v1/organizations")
    @ResponseStatus(HttpStatus.CREATED)
    OrganizationCreated createOrganization(Perimeter perimeter, @Valid @RequestBody CreateOrganizationRequest request) {
        Onboarded onboarded = people.onboard(perimeter, request.type(), request.name(), request.countryCode(),
                request.contactPhone(), request.contactEmail(), input(request.owner()));
        OrganizationResponse organization = sites.findOrganization(onboarded.organizationId())
                .map(o -> new OrganizationResponse(o.id().value(), o.type(), o.name(), o.countryCode(),
                        o.currencyCode(), request.contactPhone(), request.contactEmail(), o.active(), 0))
                .orElseThrow();
        return new OrganizationCreated(organization, result(onboarded.owner()));
    }

    /** {@code GET /v1/organizations/{id}/people}: needs {@code account:read}; filtered by name, e-mail or phone. */
    @GetMapping("/v1/organizations/{organizationId}/people")
    Page<OrganizationPersonResponse> list(Perimeter perimeter, @PathVariable UUID organizationId,
                                          @RequestParam(required = false) String q,
                                          @RequestParam(required = false) Integer page,
                                          @RequestParam(required = false) Integer size) {
        return Page.of(people.list(perimeter, new OrganizationId(organizationId), shorten(q),
                PageQuery.of(page, size)), p -> new OrganizationPersonResponse(p.person().id().value(),
                p.person().firstName(), p.person().lastName(), p.person().email(), p.person().phone(),
                p.person().status(), p.roles().stream()
                        .map(r -> new RoleAssignmentSpec(r.role().value(), new Scope(r.scopeType(), r.scopeId())))
                        .toList(),
                p.hasAdjustments(), p.siteIds().stream().map(SiteId::value).sorted().toList()));
    }

    /**
     * {@code POST /v1/organizations/{id}/invitations} (D4): an account with roles; needs {@code account:create} and
     * the right to give each role (RB-1).
     */
    @PostMapping("/v1/organizations/{organizationId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    InvitationResult inviteToOrganization(Perimeter perimeter, @PathVariable UUID organizationId,
                                          @Valid @RequestBody OrganizationInvitationRequest request) {
        return result(people.inviteToOrganization(perimeter, new OrganizationId(organizationId),
                input(request.person()), request.roles().stream()
                        .map(r -> new RoleSpec(RoleCode.of(r.roleCode()), r.scope().type(), r.scope().id()))
                        .toList()));
    }

    /**
     * {@code POST /v1/sites/{id}/invitations} (D4, E1, E5): a resident with units; needs {@code account:invite}
     * (RB-1: a manager gives only occupant roles).
     */
    @PostMapping("/v1/sites/{siteId}/invitations")
    @ResponseStatus(HttpStatus.CREATED)
    InvitationResult inviteToSite(Perimeter perimeter, @PathVariable UUID siteId,
                                  @Valid @RequestBody SiteInvitationRequest request) {
        List<OccupancyInput> units = request.occupancies() == null ? List.of() : request.occupancies();
        return result(people.inviteToSite(perimeter, new SiteId(siteId), input(request.person()),
                Optional.ofNullable(request.roleCode()).map(RoleCode::of),
                units.stream().map(MembershipController::request).toList(),
                Optional.ofNullable(request.allowedBuildingIds())));
    }

    /**
     * {@code POST /v1/organizations/{id}/people/{personId}/password-link} (D4, D5): a new "set my password" or reset
     * link, sent by e-mail to the person only; needs {@code account:invite} (RB-1).
     */
    @PostMapping("/v1/organizations/{organizationId}/people/{personId}/password-link")
    @ResponseStatus(HttpStatus.ACCEPTED)
    PasswordLinkSent sendPasswordLink(Perimeter perimeter, @PathVariable UUID organizationId,
                                      @PathVariable UUID personId) {
        PasswordLinkIssued issued = people.sendPasswordLink(perimeter, new OrganizationId(organizationId),
                new PersonId(personId));
        return new PasswordLinkSent(issued.kind().name(), issued.expiresAt());
    }

    /** The contract's invitation result; the link itself went to the person's e-mail. */
    static InvitationResult result(Invited invited) {
        InvitationIssued issued = invited.invitation();
        return new InvitationResult(invited.personId().value(), issued.existing() ? "EXISTING_ACCOUNT" : "INVITED",
                issued.linkExpiresAt().orElse(null));
    }

    /** The contract's person to the use case's. */
    private static PeopleService.PersonInput input(PersonInput p) {
        return new PeopleService.PersonInput(p.firstName(), p.lastName(), p.email(), p.phone(), p.language());
    }

    /** A free-text filter cut at 50 characters. */
    static String shorten(String q) {
        return q == null || q.length() <= 50 ? q : q.substring(0, 50);
    }

    /** A unit identifier from the contract. */
    static UnitId unit(UUID id) {
        return new UnitId(id);
    }
}
