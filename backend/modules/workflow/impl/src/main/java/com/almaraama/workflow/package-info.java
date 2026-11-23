/**
 * Module {@code workflow}: Versioned workflow definitions, site bindings, instances, tokens, events; catalogue of nodes, guards and actions (ADR-006).
 * <p>
 * Spec rules: H5, H6, H7, H9, C6. Phase P1.
 * <p>
 * Layout (hexagonal, CF-1, CF-8): {@code api} and {@code api.events} are the only packages other modules may use;
 * {@code domain}, {@code application} and {@code adapters} are internal.
 */
@ApplicationModule(
        displayName = "workflow",
        allowedDependencies = { "shared" })
package com.almaraama.workflow;

import org.springframework.modulith.ApplicationModule;
