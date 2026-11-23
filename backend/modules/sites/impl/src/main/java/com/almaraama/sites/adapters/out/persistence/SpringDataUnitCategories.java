package com.almaraama.sites.adapters.out.persistence;

import com.almaraama.sites.domain.UnitCategory;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import com.almaraama.sites.api.UnitType;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** Unit categories, schema sites. Extends {@link Repository}: no delete method exists (M2). */
interface SpringDataUnitCategories extends Repository<UnitCategory, UUID> {

    /** Loads a category. */
    Optional<UnitCategory> findById(UUID id);

    /** Several categories in one query. */
    List<UnitCategory> findByIdIn(Collection<UUID> ids);

    /** The platform defaults of one unit type, or of every type when {@code unitType} is null. */
    @Query("select c from UnitCategory c where c.scopeType = com.almaraama.shared.access.ScopeType.PLATFORM "
            + "and (:unitType is null or c.unitType = :unitType) and (:archived = true or c.active = true) "
            + "order by c.unitType, c.sortOrder, c.code")
    List<UnitCategory> platformDefaults(@Param("unitType") UnitType unitType, @Param("archived") boolean archived);

    /** Whether a platform default of this type has this code. */
    @Query("select count(c) > 0 from UnitCategory c where c.scopeType = com.almaraama.shared.access.ScopeType.PLATFORM "
            + "and c.unitType = :unitType and c.code = :code")
    boolean platformCodeTaken(@Param("unitType") UnitType unitType, @Param("code") String code);

    /** Inserts or updates a category. */
    UnitCategory save(UnitCategory category);
}
