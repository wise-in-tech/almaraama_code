package com.almaraama.assets.api;

import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import java.util.Optional;
import java.util.UUID;

/**
 * An equipment as other modules see it (CF-1): where it is (G2) and what it is (G1). Used by tickets to copy the
 * location of the equipment and filter the issue types by family (ADR-037).
 *
 * @param archived true once archived (M2): no new ticket may name it
 */
public record AssetView(UUID id, OrganizationId organizationId, SiteId siteId, UUID familyId, String name,
                        String reference, Optional<BuildingId> buildingId, Optional<Integer> floor,
                        Optional<UUID> outdoorAreaId, boolean archived) {
}
