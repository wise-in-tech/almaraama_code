package com.almaraama.membership.api;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.perimeter.Perimeter;
import java.util.List;

/**
 * Computes the perimeter of a person for one request (M3, D5, ADR-026): roles, the permissions of those roles in
 * each organization, the person's adjustments, site accesses and units. Never cached across requests, so that a
 * removed role or permission takes effect on the next request (RB-4).
 */
public interface PerimeterApi {

    /** The person's perimeter now: roles, effective permissions, visible sites, allowed buildings and units. */
    Perimeter perimeterOf(PersonId personId);

    /**
     * The people who hold this permission on the site now, computed with their real permissions (adjustments
     * included), never from role names. With {@code siteRolesFirst}, only the holders through a role on the site
     * itself, or, when there is none, the holders through the organization: the managers of a site before the
     * promoteur (K1, ADR-037). Without, every holder (routing to technicians, H4).
     */
    List<PersonId> holders(Permission permission, OrganizationId organizationId, SiteId siteId,
                           boolean siteRolesFirst);
}
