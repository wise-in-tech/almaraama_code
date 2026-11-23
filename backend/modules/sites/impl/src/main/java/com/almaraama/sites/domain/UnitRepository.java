package com.almaraama.sites.domain;

import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.sites.api.UnitType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/** Out port: units. No delete (M2). */
public interface UnitRepository {

    /**
     * Criteria of a unit search. {@code visibleBuildingIds}, when present, limits the result to those buildings
     * (a resident restricted to some buildings, C7).
     */
    record Filter(BuildingId buildingId, UUID outdoorAreaId, UnitType type, UUID categoryId, String referencePart,
                  boolean includeInactive, Optional<Set<BuildingId>> visibleBuildingIds) {
    }

    /** Loads a unit. */
    Optional<Unit> findById(UnitId id);

    /** Several units; unknown identifiers are left out. */
    List<Unit> findAllById(Collection<UnitId> ids);

    /** Units of a site matching the filter, ordered by reference. */
    PageResult<Unit> search(SiteId siteId, Filter filter, PageQuery page);

    /** Same reference in the site, ignoring case. */
    Optional<Unit> findByReference(SiteId siteId, String reference);

    /** Every unit of the site, for the import. */
    List<Unit> ofSite(SiteId siteId);

    /** Whether a building still holds active units. */
    boolean hasActiveUnitsIn(BuildingId buildingId);

    /** Whether an outdoor area still holds active units. */
    boolean hasActiveUnitsInOutdoorArea(UUID outdoorAreaId);

    /** Stores a unit. */
    Unit save(Unit unit);

    /** Stores several units in the current transaction (import). */
    List<Unit> saveAll(List<Unit> units);
}
