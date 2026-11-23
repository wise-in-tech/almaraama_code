package com.almaraama.sites.application;

import com.almaraama.shared.access.Permission;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.domain.OutdoorArea;
import com.almaraama.sites.domain.OutdoorAreaRepository;
import com.almaraama.sites.domain.OutdoorAreaType;
import com.almaraama.sites.domain.Site;
import com.almaraama.sites.domain.UnitRepository;
import java.time.Clock;
import java.util.List;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/** Outdoor areas of a site: parking, storage, playground, stadium (A3). */
@Service
public class OutdoorAreaService {

    private final OutdoorAreaRepository areas;
    private final UnitRepository units;
    private final SiteService sites;
    private final Clock clock;

    /** Wires the ports and collaborators of the outdoor area use cases. */
    OutdoorAreaService(OutdoorAreaRepository areas, UnitRepository units, SiteService sites, Clock clock) {
        this.areas = areas;
        this.units = units;
        this.sites = sites;
        this.clock = clock;
    }

    /** Outdoor areas of a visible site (A3). */
    @Transactional(readOnly = true)
    public List<OutdoorArea> list(Perimeter perimeter, SiteId siteId, boolean includeInactive) {
        sites.visible(perimeter, siteId);
        return areas.ofSite(siteId, includeInactive);
    }

    /** Creates an outdoor area; needs {@code outdoor-area:create}; {@code OUTDOOR_AREA_NAME_TAKEN}. */
    @Transactional
    public OutdoorArea create(Perimeter perimeter, SiteId siteId, OutdoorAreaType type, String name) {
        Site site = sites.visible(perimeter, siteId);
        perimeter.require(Permission.OUTDOOR_AREA_CREATE, site.organizationId(), siteId);
        requireFreeName(siteId, name, null);
        return areas.save(OutdoorArea.create(site, type, name, clock.instant()));
    }

    /**
     * Changes or archives an outdoor area; needs {@code outdoor-area:update}, and {@code outdoor-area:archive} to
     * change {@code active}; {@code OUTDOOR_AREA_HAS_ACTIVE_UNITS} when archiving one that holds units.
     */
    @Transactional
    public OutdoorArea update(Perimeter perimeter, UUID areaId, OutdoorAreaType type, String name, boolean active,
                              long version) {
        OutdoorArea area = areas.findById(areaId).orElseThrow(OrganizationService::notFound);
        Site site = sites.visible(perimeter, area.siteId());
        perimeter.require(Permission.OUTDOOR_AREA_UPDATE, site.organizationId(), site.id());
        Archiving.requireIfChanged(perimeter, Permission.OUTDOOR_AREA_ARCHIVE, area.active(), active,
                site.organizationId(), site.id());
        Versions.check(version, area.version());
        requireFreeName(site.id(), name, areaId);
        if (!active && area.active() && units.hasActiveUnitsInOutdoorArea(areaId)) {
            throw new BusinessException("OUTDOOR_AREA_HAS_ACTIVE_UNITS", ErrorCategory.RULE_VIOLATION,
                    "Deactivate or move its units first");
        }
        area.update(type, name, active, clock.instant());
        return areas.save(area);
    }

    /** {@code OUTDOOR_AREA_NAME_TAKEN} when another outdoor area of the site has this name. */
    private void requireFreeName(SiteId siteId, String name, UUID except) {
        if (name != null && areas.nameTaken(siteId, name.strip(), except)) {
            throw new BusinessException("OUTDOOR_AREA_NAME_TAKEN", ErrorCategory.CONFLICT, "Name already used");
        }
    }
}
