/**
 * Events published by module {@code customization}: versioned immutable records such as {@code SomethingHappenedV1}, carrying
 * IDs and the data consumers need, never entities (CF-5). Written to the outbox in the same transaction (CF-6).
 */
@NamedInterface("events")
package com.almaraama.customization.api.events;

import org.springframework.modulith.NamedInterface;
