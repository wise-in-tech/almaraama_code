package com.almaraama.sites.adapters.out.persistence;

import com.almaraama.sites.domain.Building;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Buildings, schema sites. No delete (M2). */
interface SpringDataBuildings extends Repository<Building, UUID> {

    /** Loads a building. */
    Optional<Building> findById(UUID id);

    /** Every building of a site by name. */
    List<Building> findBySiteIdOrderByNameAsc(UUID siteId);

    /** Active buildings of a site by name. */
    List<Building> findBySiteIdAndActiveTrueOrderByNameAsc(UUID siteId);

    /** Whether another building of the site has this name, ignoring case. */
    @Query("select count(b) > 0 from Building b where b.siteId = :siteId and lower(b.name) = lower(:name) and b.id <> :except")
    boolean nameTaken(UUID siteId, String name, UUID except);

    /** Inserts or updates a building. */
    Building save(Building building);
}
