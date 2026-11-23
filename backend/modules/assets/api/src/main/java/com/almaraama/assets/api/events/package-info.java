/**
 * Events published by module {@code assets}: versioned immutable records such as {@code SomethingHappenedV1}, carrying
 * IDs and the data consumers need, never entities (CF-5). Written to the outbox in the same transaction (CF-6).
 */
@NamedInterface("events")
package com.almaraama.assets.api.events;

import org.springframework.modulith.NamedInterface;
