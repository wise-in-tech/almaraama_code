package com.almaraama.sites.adapters.out.persistence;

import com.almaraama.sites.domain.Organization;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.Repository;

/** Organizations, schema sites. Extends {@link Repository}: no delete method exists (M2). */
interface SpringDataOrganizations extends Repository<Organization, UUID> {

    /** Loads an organization. */
    Optional<Organization> findById(UUID id);

    /** Several organizations in one query. */
    List<Organization> findByIdIn(Collection<UUID> ids);

    /** A page of all organizations by name. */
    Page<Organization> findAllByOrderByNameAsc(Pageable page);

    /** A page of the given organizations by name. */
    Page<Organization> findByIdInOrderByNameAsc(Collection<UUID> ids, Pageable page);

    /** Inserts or updates an organization. */
    Organization save(Organization organization);
}
