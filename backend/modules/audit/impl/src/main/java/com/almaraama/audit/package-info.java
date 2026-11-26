/**
 * Module {@code audit}: Append-only log of important actions.
 * <p>
 * Spec rules: M2. Phase P1.
 * <p>
 * Layout (hexagonal, CF-1, CF-8): {@code api} and {@code api.events} are the only packages other modules may use;
 * {@code domain}, {@code application} and {@code adapters} are internal.
 */
@ApplicationModule(
        displayName = "audit",
        allowedDependencies = { "shared", "identity :: events", "maintenance :: events", "membership :: events", "workflow :: events", "assets :: events",
                "sites :: events", "customization :: events" })
package com.almaraama.audit;

import org.springframework.modulith.ApplicationModule;
