/**
 * Application assembly (ADR-023): cross-cutting configuration that belongs to no bounded context, such as the web
 * security chain, and later the JWT filter that builds the {@code Perimeter} (CF-11) through the membership API.
 * <p>
 * Business modules never depend on this package.
 */
@ApplicationModule(displayName = "app")
package com.almaraama.app;

import org.springframework.modulith.ApplicationModule;
