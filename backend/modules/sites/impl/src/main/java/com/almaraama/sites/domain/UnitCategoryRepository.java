package com.almaraama.sites.domain;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.sites.api.UnitType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Out port: unit categories (A6). Never deleted (M2). */
public interface UnitCategoryRepository {

    /** Loads a category. */
    Optional<UnitCategory> findById(UUID id);

    /** Several categories, to show the units' categories; unknown identifiers are left out. */
    List<UnitCategory> findAllById(Collection<UUID> ids);

    /**
     * The categories usable in an organization, and on one site when {@code site} is given: platform defaults, the
     * organization's and the site's own; by unit type then sort order.
     */
    List<UnitCategory> usable(OrganizationId organization, Optional<SiteId> site, UnitType unitType,
                              boolean includeArchived);

    /** Whether the same scope already has a category of this type and code. */
    boolean codeTaken(OrganizationId organization, Optional<SiteId> site, UnitType unitType, String code);

    /** The platform defaults, by unit type then sort order (A6, M7). */
    List<UnitCategory> platformDefaults(UnitType unitType, boolean includeArchived);

    /** Whether a platform default of this type already has this code. */
    boolean platformCodeTaken(UnitType unitType, String code);

    /** Stores a category. */
    UnitCategory save(UnitCategory category);
}
