package com.almaraama.sites.adapters.out.persistence;

import com.almaraama.sites.domain.Unit;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Units, schema sites. No delete (M2). Searches with optional criteria go through {@link JpaUnitSearch}. */
interface SpringDataUnits extends Repository<Unit, UUID> {

    /** Loads a unit. */
    Optional<Unit> findById(UUID id);

    /** Several units in one query. */
    List<Unit> findByIdIn(Collection<UUID> ids);

    /** The unit of the site with this reference, ignoring case. */
    @Query("select u from Unit u where u.siteId = :siteId and lower(u.reference) = lower(:reference)")
    Optional<Unit> findByReference(UUID siteId, String reference);

    /** Every unit of a site. */
    List<Unit> findBySiteId(UUID siteId);

    /** Whether a building holds active units. */
    boolean existsByBuildingIdAndActiveTrue(UUID buildingId);

    /** Whether an outdoor area holds active units. */
    boolean existsByOutdoorAreaIdAndActiveTrue(UUID outdoorAreaId);

    /** Inserts or updates a unit. */
    Unit save(Unit unit);

    /** Inserts several units. */
    List<Unit> saveAll(Iterable<Unit> units);
}
