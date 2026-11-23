package com.almaraama.sites.domain;

import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.SiteId;
import java.util.List;
import java.util.Optional;

/** Out port: buildings. No delete (M2). */
public interface BuildingRepository {

    /** Loads a building. */
    Optional<Building> findById(BuildingId id);

    /** Buildings of a site ordered by name. */
    List<Building> ofSite(SiteId siteId, boolean includeInactive);

    /**
     * Whether another building of the site has this name, ignoring case; {@code except} is the building being renamed.
     */
    boolean nameTaken(SiteId siteId, String name, BuildingId except);

    /** Stores a building. */
    Building save(Building building);
}
