/**
 * Shared kernel (CF-9): typed IDs, {@code Money} in DZD, {@code Perimeter}, business errors, clock, ar/fr labels.
 * <p>
 * Kept minimal on purpose. It depends on no other module, and every module may depend on it. Plain Java only: no
 * Spring, JPA or web types, so module domains can use it without breaking CF-8.
 * <p>
 * Spec rules: M1, M3.
 */
@ApplicationModule(displayName = "shared", type = ApplicationModule.Type.OPEN)
package com.almaraama.shared;

import org.springframework.modulith.ApplicationModule;
