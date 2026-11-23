package com.almaraama.membership.adapters.out.persistence;

import com.almaraama.membership.domain.Occupancy;
import com.almaraama.membership.domain.OccupancyRole;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;

/** Occupancies, schema membership. No delete (E2, M2). */
interface SpringDataOccupancies extends Repository<Occupancy, UUID> {

    /** Loads an occupancy. */
    Optional<Occupancy> findById(UUID id);

    /** Every occupancy of a unit. */
    List<Occupancy> findByUnitIdOrderByEndedOnDescStartedOnDesc(UUID unitId);

    /** Current occupancies of a unit, newest first. */
    List<Occupancy> findByUnitIdAndEndedOnIsNullOrderByStartedOnDesc(UUID unitId);

    /** Current occupancies of a person. */
    List<Occupancy> findByPersonIdAndEndedOnIsNull(UUID personId);

    /** Current occupancies of a person in one site. */
    List<Occupancy> findByPersonIdAndSiteIdAndEndedOnIsNull(UUID personId, UUID siteId);

    /** The current occupant of a unit with this role (the owner, E5). */
    Optional<Occupancy> findByUnitIdAndRoleAndEndedOnIsNull(UUID unitId, OccupancyRole role);

    /** The current primary tenant of a unit (E3). */
    Optional<Occupancy> findByUnitIdAndPrimaryTenantTrueAndEndedOnIsNull(UUID unitId);

    /** The person's current occupancy of the unit. */
    Optional<Occupancy> findByPersonIdAndUnitIdAndEndedOnIsNull(UUID personId, UUID unitId);

    /** Inserts or updates an occupancy. */
    Occupancy save(Occupancy occupancy);
}
