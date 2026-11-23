package com.almaraama.sites.domain;

import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Out port: the zone tree (A8). Never deleted (M2). */
public interface ZoneRepository {

    /**
     * Criteria of a zone listing: children of {@code parentId} (level 1 when absent), only one {@code level}, or a
     * {@code namePart} search across levels. Deactivated zones are never listed; the caller filters provisional zones.
     */
    record Filter(String countryCode, UUID parentId, Integer level, String namePart) {
    }

    /** Criteria of the back-office listing: every status, optionally one, under a parent, or by name or code. */
    record AdminFilter(String countryCode, ZoneStatus status, UUID parentId, String namePart) {
    }

    /** One page of zones of every status matching the back-office filter, by code (ADR-035). */
    PageResult<Zone> adminSearch(AdminFilter filter, PageQuery page);

    /** The children of a zone, every status. */
    List<Zone> children(UUID parentId);

    /** Whether another zone of the country has this code ({@code except} may be {@code null}). */
    boolean codeTaken(String countryCode, String code, UUID except);

    /** Loads a zone. */
    Optional<Zone> findById(UUID id);

    /** Several zones, for ancestors and addresses; unknown identifiers are left out. */
    List<Zone> findAllById(Collection<UUID> ids);

    /** One page of zones matching the filter, by code. */
    PageResult<Zone> search(Filter filter, PageQuery page);

    /** Whether a zone of this country already has this name, in any language, under the same parent. */
    boolean nameTaken(String countryCode, UUID parentId, LocalizedName name);

    /** Stores a zone. */
    Zone save(Zone zone);
}
