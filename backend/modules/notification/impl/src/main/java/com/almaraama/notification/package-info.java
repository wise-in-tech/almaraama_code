/**
 * Module {@code notification}: In-app notifications, unread counter, subscriptions to other modules' events.
 * <p>
 * Spec rules: K1 to K6. Phase P1.
 * <p>
 * Layout (hexagonal, CF-1, CF-8): {@code api} and {@code api.events} are the only packages other modules may use;
 * {@code domain}, {@code application} and {@code adapters} are internal.
 */
@ApplicationModule(
        displayName = "notification",
        allowedDependencies = { "shared", "maintenance :: events", "membership :: events", "preventive :: events" })
package com.almaraama.notification;

import org.springframework.modulith.ApplicationModule;
