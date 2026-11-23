/**
 * Module {@code sites}: Organization, Site, Building, OutdoorArea, Unit, annexes, vocabulary per client type, Excel import.
 * <p>
 * Spec rules: A1 to A6. Phase P1.
 * <p>
 * Layout (hexagonal, CF-1, CF-8): {@code api} and {@code api.events} are the only packages other modules may use;
 * {@code domain}, {@code application} and {@code adapters} are internal.
 */
@ApplicationModule(
        displayName = "sites",
        allowedDependencies = { "shared", "spreadsheet" })
package com.almaraama.sites;

import org.springframework.modulith.ApplicationModule;
