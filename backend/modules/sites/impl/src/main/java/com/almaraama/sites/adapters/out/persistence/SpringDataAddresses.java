package com.almaraama.sites.adapters.out.persistence;

import com.almaraama.sites.domain.Address;
import com.almaraama.sites.domain.AddressOwnerType;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;
import org.springframework.data.jpa.repository.Query;

/** Addresses, schema sites. No delete (M2): a replaced address keeps its row. */
interface SpringDataAddresses extends Repository<Address, UUID> {

    /** The owner's current address. */
    Optional<Address> findByOwnerTypeAndOwnerIdAndReplacedAtIsNull(AddressOwnerType ownerType, UUID ownerId);

    /** The current addresses of several owners. */
    List<Address> findByOwnerTypeAndOwnerIdInAndReplacedAtIsNull(AddressOwnerType ownerType, Collection<UUID> ids);

    /** The current addresses attached to a zone (merge of a provisional zone, A8). */
    List<Address> findByZoneIdAndReplacedAtIsNull(UUID zoneId);

    /** Number of current addresses of each of these zones, as (zone, count) pairs. */
    @Query("select a.zoneId, count(a) from Address a where a.replacedAt is null and a.zoneId in :zoneIds "
            + "group by a.zoneId")
    List<Object[]> countCurrentByZone(@Param("zoneIds") Collection<UUID> zoneIds);

    /** Inserts or updates an address. */
    Address save(Address address);

    /** Inserts or updates an address and flushes at once. */
    Address saveAndFlush(Address address);
}
