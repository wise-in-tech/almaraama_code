package com.almaraama.sites.adapters.out.persistence;

import com.almaraama.sites.domain.Zone;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Zones of the tree, schema sites. Extends {@link Repository}: no delete method exists (M2). */
interface SpringDataZones extends Repository<Zone, UUID> {

    /** Loads a zone. */
    Optional<Zone> findById(UUID id);

    /** Several zones in one query. */
    List<Zone> findByIdIn(Collection<UUID> ids);

    /** The children of a zone. */
    List<Zone> findByParentId(UUID parentId);

    /** Whether another zone of the country has this code. */
    boolean existsByCountryCodeAndCodeAndIdNot(String countryCode, String code, UUID id);

    /** Inserts or updates a zone. */
    Zone save(Zone zone);
}
