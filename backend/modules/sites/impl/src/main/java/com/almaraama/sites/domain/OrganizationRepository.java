package com.almaraama.sites.domain;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import java.util.Map;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Out port: organizations. No delete (M2). */
public interface OrganizationRepository {

    /** Loads an organization. */
    Optional<Organization> findById(OrganizationId id);

    /** Several organizations; unknown identifiers are left out. */
    List<Organization> findAllById(Collection<OrganizationId> ids);

    /** A page of every organization by name. */
    PageResult<Organization> page(PageQuery page);

    /** A page of the given organizations by name. */
    PageResult<Organization> page(Collection<OrganizationId> ids, PageQuery page);

    /** Back-office criteria: part of the name, state and country, each optional. */
    record Search(String namePart, Boolean active, String countryCode) {
    }

    /** One page of every organization matching the criteria, by name (ADR-035). */
    PageResult<Organization> search(Search search, PageQuery page);

    /** Number of sites of each organization that has some. */
    Map<OrganizationId, Long> siteCounts(Collection<OrganizationId> ids);

    /** Stores an organization. */
    Organization save(Organization organization);
}
