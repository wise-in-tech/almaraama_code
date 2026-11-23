/**
 * Module {@code files}: Upload, type and size checks, photo re-encoding, short-lived signed links.
 * <p>
 * Spec rules: H10, G3, J2. Phase P1.
 * <p>
 * Layout (hexagonal, CF-1, CF-8): {@code api} and {@code api.events} are the only packages other modules may use;
 * {@code domain}, {@code application} and {@code adapters} are internal.
 */
@ApplicationModule(
        displayName = "files",
        allowedDependencies = { "shared" })
package com.almaraama.files;

import org.springframework.modulith.ApplicationModule;
