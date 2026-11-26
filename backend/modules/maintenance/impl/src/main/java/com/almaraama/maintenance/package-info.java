/**
 * Module {@code maintenance}: IssueCategory, IssueType, routing and visibility per site, Ticket, photos, TicketAssignment, Quote.
 * <p>
 * Spec rules: H1 to H10, J1 to J3. Phase P1.
 * <p>
 * Layout (hexagonal, CF-1, CF-8): {@code api} and {@code api.events} are the only packages other modules may use;
 * {@code domain}, {@code application} and {@code adapters} are internal.
 */
@ApplicationModule(
        displayName = "maintenance",
        allowedDependencies = { "shared", "identity :: api", "sites :: api", "membership :: api", "assets :: api", "files :: api", "workflow :: api" })
package com.almaraama.maintenance;

import org.springframework.modulith.ApplicationModule;
