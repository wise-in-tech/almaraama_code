package com.almaraama.sites.domain;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

/** Out port: sites. No delete (M2). */
public interface SiteRepository {

    /** Loads a site. */
    Optional<Site> findById(SiteId id);

    /** Several sites; unknown identifiers are left out. */
    List<Site> findAllById(Collection<SiteId> ids);

    /**
     * Criteria of a site listing (criterion 3): {@code zonePath}, the materialized path of a zone at any level (the
     * sites whose address is in that zone or below it), part of the name, and whether archived sites are included.
     */
    record Filter(String zonePath, String namePart, boolean includeInactive) {
    }

    /** Sites of an organization matching the filter, by name; limited to {@code onlyIds} when given. */
    PageResult<Site> page(OrganizationId organizationId, Collection<SiteId> onlyIds, Filter filter, PageQuery page);

    /** Number of sites of an organization, archived ones included (single-site rule of {@code RESIDENCE}). */
    long countOf(OrganizationId organizationId);

    /** Stores a site. */
    Site save(Site site);
}
