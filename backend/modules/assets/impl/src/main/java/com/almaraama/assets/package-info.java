/**
 * Module {@code assets}: AssetFamily, AssetModel, Asset, QR code, Supplier, Document, Warranty.
 * <p>
 * Spec rules: G1 to G4. Phase P1.
 * <p>
 * Layout (hexagonal, CF-1, CF-8): {@code api} and {@code api.events} are the only packages other modules may use;
 * {@code domain}, {@code application} and {@code adapters} are internal.
 */
@ApplicationModule(
        displayName = "assets",
        allowedDependencies = { "shared", "sites :: api", "customization :: api" })
package com.almaraama.assets;

import org.springframework.modulith.ApplicationModule;
