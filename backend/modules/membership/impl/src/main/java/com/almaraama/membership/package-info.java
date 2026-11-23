/**
 * Module {@code membership}: SiteMembership, OrganizationMembership, self-registration, allowed buildings, Occupancy, Perimeter computation.
 * <p>
 * Spec rules: C1 to C7, D3, D5, E1 to E4. Phase P1.
 * <p>
 * Layout (hexagonal, CF-1, CF-8): {@code api} and {@code api.events} are the only packages other modules may use;
 * {@code domain}, {@code application} and {@code adapters} are internal.
 */
@ApplicationModule(
        displayName = "membership",
        allowedDependencies = { "shared", "spreadsheet", "identity :: api", "identity :: events", "sites :: api",
                "sites :: events" })
package com.almaraama.membership;

import org.springframework.modulith.ApplicationModule;
