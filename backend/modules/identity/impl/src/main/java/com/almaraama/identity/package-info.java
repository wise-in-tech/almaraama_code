/**
 * Module {@code identity}: Person, phone (+213), hashed PIN, single-use Invitation, TrustedDevice, sessions, SMS code.
 * <p>
 * Spec rules: D1, D2, D4, D6. Phase P1.
 * <p>
 * Layout (hexagonal, CF-1, CF-8): {@code api} and {@code api.events} are the only packages other modules may use;
 * {@code domain}, {@code application} and {@code adapters} are internal.
 */
@ApplicationModule(
        displayName = "identity",
        allowedDependencies = { "shared" })
package com.almaraama.identity;

import org.springframework.modulith.ApplicationModule;
