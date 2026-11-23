package com.almaraama.sites.api;

import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.SiteId;

/** Public view of a building for other modules. */
public record BuildingSummary(BuildingId id, SiteId siteId, String name, boolean active) {
}
