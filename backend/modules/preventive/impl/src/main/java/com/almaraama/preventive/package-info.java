/**
 * Module {@code preventive}: PreventiveRule per family or asset, daily job, ChecklistLine, regulatory deadlines.
 * <p>
 * Spec rules: I1 to I5. Phase P1.
 * <p>
 * Layout (hexagonal, CF-1, CF-8): {@code api} and {@code api.events} are the only packages other modules may use;
 * {@code domain}, {@code application} and {@code adapters} are internal.
 */
@ApplicationModule(
        displayName = "preventive",
        allowedDependencies = { "shared", "assets :: api", "maintenance :: api" })
package com.almaraama.preventive;

import org.springframework.modulith.ApplicationModule;
