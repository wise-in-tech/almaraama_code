package com.almaraama.sites.api;

import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.id.UnitId;
import java.util.Optional;

/** Public view of a unit for other modules (E1). */
public record UnitSummary(UnitId id, OrganizationId organizationId, SiteId siteId, Optional<BuildingId> buildingId,
                          UnitType type, String reference, boolean active) {
}
