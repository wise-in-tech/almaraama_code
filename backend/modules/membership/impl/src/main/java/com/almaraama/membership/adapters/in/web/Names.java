package com.almaraama.membership.adapters.in.web;

import com.almaraama.identity.api.IdentityApi;
import com.almaraama.identity.api.PersonSummary;
import com.almaraama.membership.adapters.in.web.MembershipWebDtos.PersonRef;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.actor.ActorType;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.sites.api.OrganizationSummary;
import com.almaraama.sites.api.SiteSummary;
import com.almaraama.sites.api.SitesApi;
import java.util.Collection;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** Resolves, in batches, the names shown next to identifiers in responses (people, organizations, sites). */
@Component
class Names {

    private static final PersonRef PLATFORM = new PersonRef(Actor.PLATFORM_ID, "almaraama", "");

    private final IdentityApi identity;
    private final SitesApi sites;

    /**
     * @param identity names of people
     * @param sites names of organizations and sites
     */
    Names(IdentityApi identity, SitesApi sites) {
        this.identity = identity;
        this.sites = sites;
    }

    /** People by identifier, in one query; the platform actor is named {@code almaraama}. */
    Map<UUID, PersonRef> people(Collection<UUID> ids) {
        Map<UUID, PersonRef> refs = new HashMap<>();
        refs.put(Actor.PLATFORM_ID, PLATFORM);
        identity.findPersons(ids.stream().filter(Objects::nonNull).filter(id -> !id.equals(Actor.PLATFORM_ID))
                        .distinct().map(PersonId::new).toList())
                .forEach(p -> refs.put(p.id().value(), ref(p)));
        return refs;
    }

    /** One person, or {@code null} for a missing identifier. */
    PersonRef person(UUID id) {
        return id == null ? null : people(List.of(id)).get(id);
    }

    /** A person reference from a profile, {@code null} when unknown. */
    static PersonRef ref(PersonSummary p) {
        return p == null ? null : new PersonRef(p.id().value(), p.firstName(), p.lastName());
    }

    /** The identifier to name an actor by: the person, or the platform for any system actor. */
    static UUID id(Actor actor) {
        return actor.type() == ActorType.PERSON ? actor.id() : Actor.PLATFORM_ID;
    }

    /** Names of organizations and sites, by identifier. */
    Map<UUID, String> scopes(Collection<UUID> organizationIds, Collection<UUID> siteIds) {
        Map<UUID, String> names = new HashMap<>();
        sites.findOrganizations(organizationIds.stream().filter(Objects::nonNull).distinct()
                .map(OrganizationId::new).toList()).forEach(o -> names.put(o.id().value(), o.name()));
        sites.findSites(siteIds.stream().filter(Objects::nonNull).distinct().map(SiteId::new).toList())
                .forEach(s -> names.put(s.id().value(), s.name()));
        return names;
    }

    /** Organizations by identifier. */
    Map<UUID, OrganizationSummary> organizations(Collection<OrganizationId> ids) {
        Map<UUID, OrganizationSummary> map = new HashMap<>();
        sites.findOrganizations(ids).forEach(o -> map.put(o.id().value(), o));
        return map;
    }

    /** Sites by identifier. */
    Map<UUID, SiteSummary> sites(Collection<SiteId> ids) {
        Map<UUID, SiteSummary> map = new HashMap<>();
        sites.findSites(ids).forEach(s -> map.put(s.id().value(), s));
        return map;
    }
}
