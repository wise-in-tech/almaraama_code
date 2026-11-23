/**
 * Module {@code customization}: the dictionary of the interface texts (M6, ADR-029, sprint 2); attribute
 * definitions, configurable lookups, jsonb value validation; later per-client entity types and screens (ADR-021).
 * <p>
 * Spec rules: M6, C12, G3, H2. Phase P1.
 * <p>
 * Layout (hexagonal, CF-1, CF-8): {@code api} and {@code api.events} are the only packages other modules may use;
 * {@code domain}, {@code application} and {@code adapters} are internal.
 */
@ApplicationModule(
        displayName = "customization",
        allowedDependencies = { "shared", "identity :: api" })
package com.almaraama.customization;

import org.springframework.modulith.ApplicationModule;
