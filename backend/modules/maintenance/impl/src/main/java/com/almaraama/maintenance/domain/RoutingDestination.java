package com.almaraama.maintenance.domain;

/** Who receives a new ticket of a type (H4). Glossary {@code RoutingDestination}. */
public enum RoutingDestination {
    /** The managers of the site. */
    SITE_MANAGER,
    /** One or more designated technicians (the managers are notified too, K1). */
    TECHNICIANS
}
