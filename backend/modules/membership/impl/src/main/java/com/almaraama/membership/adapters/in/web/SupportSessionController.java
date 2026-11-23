package com.almaraama.membership.adapters.in.web;

import com.almaraama.membership.adapters.in.web.MembershipWebDtos.InvitationResult;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.OrganizationRef;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.Page;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.PersonInput;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.PersonRef;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.StartSupportSessionRequest;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.SupportSessionResponse;
import com.almaraama.membership.application.PeopleService;
import com.almaraama.membership.application.SupportSessionService;
import com.almaraama.membership.domain.SupportSession;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.OrganizationSummary;
import jakarta.validation.Valid;
import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

/**
 * Back-office of the platform administrators on people (C8, C12, ADR-035): support sessions on an organization, and
 * the invitation of one more promoteur.
 */
@RestController
class SupportSessionController {

    private final SupportSessionService sessions;
    private final PeopleService people;
    private final Names names;

    /** Wires the support sessions, the people use cases and the names shown in responses. */
    SupportSessionController(SupportSessionService sessions, PeopleService people, Names names) {
        this.sessions = sessions;
        this.people = people;
        this.names = names;
    }

    /** {@code GET /v1/admin/support-sessions}: needs {@code support:access}. */
    @GetMapping("/v1/admin/support-sessions")
    Page<SupportSessionResponse> list(Perimeter perimeter, @RequestParam(required = false) UUID organizationId,
                                      @RequestParam(defaultValue = "false") boolean openOnly,
                                      @RequestParam(required = false) Integer page,
                                      @RequestParam(required = false) Integer size) {
        PageResult<SupportSession> found = sessions.list(perimeter,
                Optional.ofNullable(organizationId).map(OrganizationId::new), openOnly, PageQuery.of(page, size));
        Map<UUID, OrganizationRef> organizations = organizations(found.items());
        Map<UUID, PersonRef> administrators = names.people(found.items().stream().map(s -> s.personId().value())
                .collect(Collectors.toSet()));
        Instant now = sessions.now();
        return Page.of(found, s -> response(s, organizations, administrators, now));
    }

    /** {@code POST /v1/admin/support-sessions}: opens a session with a reason, for one hour (C12). */
    @PostMapping("/v1/admin/support-sessions")
    @ResponseStatus(HttpStatus.CREATED)
    SupportSessionResponse start(Perimeter perimeter, @Valid @RequestBody StartSupportSessionRequest request) {
        return response(sessions.start(perimeter, new OrganizationId(request.organizationId()), request.reason()));
    }

    /** {@code POST /v1/admin/support-sessions/{id}/end}: ends it early; only its administrator. */
    @PostMapping("/v1/admin/support-sessions/{supportSessionId}/end")
    SupportSessionResponse end(Perimeter perimeter, @PathVariable UUID supportSessionId) {
        return response(sessions.end(perimeter, supportSessionId));
    }

    /** {@code POST /v1/admin/organizations/{id}/owner-invitations}: one more promoteur (C8); needs our team. */
    @PostMapping("/v1/admin/organizations/{organizationId}/owner-invitations")
    @ResponseStatus(HttpStatus.CREATED)
    InvitationResult inviteOwner(Perimeter perimeter, @PathVariable UUID organizationId,
                                 @Valid @RequestBody PersonInput request) {
        PeopleService.Invited invited = people.inviteOwner(perimeter, new OrganizationId(organizationId),
                new PeopleService.PersonInput(request.firstName(), request.lastName(), request.email(),
                        request.phone(), request.language()));
        return PeopleController.result(invited);
    }

    /** One session with the names of its organization and administrator. */
    SupportSessionResponse response(SupportSession session) {
        return response(session, organizations(List.of(session)), names.people(List.of(session.personId().value())),
                sessions.now());
    }

    /** Names of the organizations of these sessions, in one query. */
    private Map<UUID, OrganizationRef> organizations(Collection<SupportSession> list) {
        Map<UUID, OrganizationSummary> found = names.organizations(list.stream().map(SupportSession::organizationId)
                .collect(Collectors.toSet()));
        return found.values().stream().collect(Collectors.toMap(o -> o.id().value(),
                o -> new OrganizationRef(o.id().value(), o.name())));
    }

    /** Response body of a session; its status is computed at this instant. */
    private static SupportSessionResponse response(SupportSession s, Map<UUID, OrganizationRef> organizations,
                                                   Map<UUID, PersonRef> administrators, Instant now) {
        return new SupportSessionResponse(s.id(), organizations.get(s.organizationId().value()),
                administrators.get(s.personId().value()), s.reason(), s.status(now).name(), s.startedAt(),
                s.expiresAt(), s.endedAt().orElse(null));
    }
}
