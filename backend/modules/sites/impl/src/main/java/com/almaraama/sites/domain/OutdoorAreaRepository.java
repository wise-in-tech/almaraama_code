package com.almaraama.sites.domain;

import com.almaraama.shared.id.SiteId;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Out port: outdoor areas. No delete (M2). */
public interface OutdoorAreaRepository {

    /** Loads an outdoor area. */
    Optional<OutdoorArea> findById(UUID id);

    /** Outdoor areas of a site by name. */
    List<OutdoorArea> ofSite(SiteId siteId, boolean includeInactive);

    /** Whether another outdoor area of the site has this name, ignoring case. */
    boolean nameTaken(SiteId siteId, String name, UUID except);

    /** Stores an outdoor area. */
    OutdoorArea save(OutdoorArea area);
}
