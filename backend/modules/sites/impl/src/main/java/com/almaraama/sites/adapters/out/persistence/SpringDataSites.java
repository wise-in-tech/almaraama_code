package com.almaraama.sites.adapters.out.persistence;

import com.almaraama.sites.domain.Site;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

/** Sites, schema sites. No delete (M2). */
interface SpringDataSites extends Repository<Site, UUID> {

    /** Loads a site. */
    Optional<Site> findById(UUID id);

    /** Several sites in one query. */
    List<Site> findByIdIn(Collection<UUID> ids);

    /** A page of an organization's sites by name. */
    Page<Site> findByOrganizationIdOrderByNameAsc(UUID organizationId, Pageable page);

    /** A page of the given sites of an organization by name. */
    Page<Site> findByOrganizationIdAndIdInOrderByNameAsc(UUID organizationId, Collection<UUID> ids, Pageable page);

    /** Number of sites of an organization. */
    long countByOrganizationId(UUID organizationId);

    /** Number of sites of each of these organizations, as (organization, count) pairs. */
    @Query("select s.organizationId, count(s) from Site s where s.organizationId in :ids group by s.organizationId")
    List<Object[]> countByOrganizations(@Param("ids") Collection<UUID> ids);

    /** Inserts or updates a site. */
    Site save(Site site);
}
