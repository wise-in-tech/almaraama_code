package com.almaraama.sites.application;

import com.almaraama.shared.id.SiteId;
import com.almaraama.sites.application.Views.AddressView;
import com.almaraama.sites.application.Views.ZoneView;
import com.almaraama.sites.domain.Address;
import com.almaraama.sites.domain.AddressOwnerType;
import com.almaraama.sites.domain.AddressRepository;
import com.almaraama.sites.domain.Organization;
import java.time.Instant;
import java.util.Collection;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

/**
 * Addresses of sites and buildings (A9): attached to a zone of the attachment level of the organization's country,
 * replaced rather than changed (history, M2). A new building receives a copy of its site's address; the copy never
 * follows later changes of the site's address (decision of 20 November 2026).
 */
@Component
class AddressBook {

    private static final Logger LOG = LoggerFactory.getLogger(AddressBook.class);

    private final AddressRepository addresses;
    private final ZoneBook zones;

    /**
     * @param addresses the address table
     * @param zones     checks and resolves the zones
     */
    AddressBook(AddressRepository addresses, ZoneBook zones) {
        this.addresses = addresses;
        this.zones = zones;
    }

    /**
     * Sets the owner's current address: checks its zone (see {@link ZoneBook#requireAddressZone}), keeps the current
     * address when nothing changed, otherwise replaces it.
     */
    void set(Organization organization, SiteId siteId, AddressOwnerType ownerType, UUID ownerId, Address.Spec spec,
             Instant now) {
        zones.requireAddressZone(organization, spec.zoneId());
        Optional<Address> current = addresses.current(ownerType, ownerId);
        if (current.isPresent() && current.get().sameAs(spec)) {
            return;
        }
        current.ifPresent(address -> {
            address.replace(now);
            addresses.saveReplaced(address);
        });
        addresses.save(Address.of(organization.id(), siteId, ownerType, ownerId, spec, now));
        LOG.debug("Address of {} {} set in zone {}", ownerType, ownerId, spec.zoneId());
    }

    /** Gives a new building a copy of its site's current address (A9, origin {@code SITE_COPY}). */
    void copySiteAddressTo(SiteId siteId, UUID buildingId, Instant now) {
        Address site = addresses.current(AddressOwnerType.SITE, siteId.value())
                .orElseThrow(() -> new IllegalStateException("Site without address: " + siteId));
        addresses.save(Address.copyForBuilding(site, buildingId, now));
        LOG.debug("Building {} received a copy of the address of site {}", buildingId, siteId.value());
    }

    /** The owner's current address with its zone. */
    Optional<AddressView> current(AddressOwnerType ownerType, UUID ownerId) {
        return addresses.current(ownerType, ownerId).map(address ->
                new AddressView(address, zones.view(address.zoneId())));
    }

    /** The current addresses of several owners with their zones, resolved in a few queries. */
    Map<UUID, AddressView> current(AddressOwnerType ownerType, Collection<UUID> ownerIds) {
        Map<UUID, Address> found = addresses.current(ownerType, ownerIds);
        Map<UUID, ZoneView> views = zones.views(found.values().stream().map(Address::zoneId)
                .collect(Collectors.toSet()));
        Map<UUID, AddressView> result = new HashMap<>();
        found.forEach((owner, address) -> result.put(owner, new AddressView(address, views.get(address.zoneId()))));
        return result;
    }
}
