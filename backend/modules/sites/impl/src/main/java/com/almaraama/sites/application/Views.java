package com.almaraama.sites.application;

import com.almaraama.sites.domain.Address;
import com.almaraama.sites.domain.Building;
import com.almaraama.sites.domain.Site;
import com.almaraama.sites.domain.Unit;
import com.almaraama.sites.domain.UnitCategory;
import com.almaraama.sites.domain.Zone;
import java.util.List;

/** What the use cases return to the web adapter: aggregates with the data resolved for display. */
public final class Views {

    /** Holder of the view records only. */
    private Views() {
    }

    /** A zone with its ancestors, from level 1 down to its parent (for a commune: its wilaya). */
    public record ZoneView(Zone zone, List<Zone> ancestors) {
    }

    /** An address with its zone resolved (A9), and the country of that zone. */
    public record AddressView(Address address, ZoneView zone) {
    }

    /** A site with its current address and the country of its organization (A1). */
    public record SiteView(Site site, String countryCode, AddressView address) {
    }

    /** A building with its own current address, a copy of the site's or typed for it (A9, criterion 18). */
    public record BuildingView(Building building, AddressView address) {
    }

    /** A unit with its category (A6). */
    public record UnitView(Unit unit, UnitCategory category) {
    }
}
