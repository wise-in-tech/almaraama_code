package com.almaraama.sites.domain;

import java.util.List;
import java.util.Collection;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

/** Out port: addresses. Only the current address of each owner is read; replaced ones stay (M2). */
public interface AddressRepository {

    /** The owner's current address, ignoring replaced ones. */
    Optional<Address> current(AddressOwnerType ownerType, UUID ownerId);

    /** The current address of each owner that has one, by owner identifier. */
    Map<UUID, Address> current(AddressOwnerType ownerType, Collection<UUID> ownerIds);

    /**
     * Stores an address that stops being current and writes it to the database at once, before its replacement is
     * inserted (the unique index allows one current address per owner).
     */
    Address saveReplaced(Address address);

    /** The current addresses attached to a zone (A8 merge). */
    List<Address> currentInZone(UUID zoneId);

    /** Number of current addresses of each zone that has some. */
    Map<UUID, Long> currentCountByZone(Collection<UUID> zoneIds);

    /** Stores an address. */
    Address save(Address address);
}
