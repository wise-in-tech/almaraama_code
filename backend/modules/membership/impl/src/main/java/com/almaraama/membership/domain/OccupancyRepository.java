package com.almaraama.membership.domain;

import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Out port: occupancies. No delete (E2, M2). */
public interface OccupancyRepository {

    /** Loads an occupancy. */
    Optional<Occupancy> findById(UUID id);

    /** Occupancies of a unit, current ones first. */
    List<Occupancy> ofUnit(UnitId unitId, boolean includeEnded);

    /** The person's occupancies not ended. */
    List<Occupancy> activeOfPerson(PersonId personId);

    /** The person's occupancies not ended in one site. */
    List<Occupancy> activeOfPersonInSite(PersonId personId, SiteId siteId);

    /** The unit's current owner (E5). */
    Optional<Occupancy> activeOwner(UnitId unitId);

    /** The unit's current primary tenant (E3). */
    Optional<Occupancy> activePrimaryTenant(UnitId unitId);

    /** The person's current occupancy of the unit. */
    Optional<Occupancy> active(PersonId personId, UnitId unitId);

    /** Stores an occupancy. */
    Occupancy save(Occupancy occupancy);
}
