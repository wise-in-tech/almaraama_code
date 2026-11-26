package com.almaraama.sites.api;

import com.almaraama.shared.id.SiteId;
import java.util.UUID;

/**
 * An outdoor area of a site (A3) as other modules see it: equipment and tickets may be located there (G2).
 *
 * @param active false once archived (M2)
 */
public record OutdoorAreaSummary(UUID id, SiteId siteId, String name, boolean active) {
}
