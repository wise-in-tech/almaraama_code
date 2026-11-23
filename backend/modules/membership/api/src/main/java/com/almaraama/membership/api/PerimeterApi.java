package com.almaraama.membership.api;

import com.almaraama.shared.id.PersonId;
import com.almaraama.shared.perimeter.Perimeter;

/**
 * Computes the perimeter of a person for one request (M3, D5, ADR-026): roles, the permissions of those roles in
 * each organization, the person's adjustments, site accesses and units. Never cached across requests, so that a
 * removed role or permission takes effect on the next request (RB-4).
 */
public interface PerimeterApi {

    /** The person's perimeter now: roles, effective permissions, visible sites, allowed buildings and units. */
    Perimeter perimeterOf(PersonId personId);
}
