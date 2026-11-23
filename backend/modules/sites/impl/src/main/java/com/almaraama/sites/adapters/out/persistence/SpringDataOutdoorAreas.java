package com.almaraama.sites.adapters.out.persistence;

import com.almaraama.sites.domain.OutdoorArea;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;

/** Outdoor areas, schema sites. No delete (M2). */
interface SpringDataOutdoorAreas extends Repository<OutdoorArea, UUID> {

    /** Loads an outdoor area. */
    Optional<OutdoorArea> findById(UUID id);

    /** Every outdoor area of a site by name. */
    List<OutdoorArea> findBySiteIdOrderByNameAsc(UUID siteId);

    /** Active outdoor areas of a site by name. */
    List<OutdoorArea> findBySiteIdAndActiveTrueOrderByNameAsc(UUID siteId);

    /** Whether another outdoor area of the site has this name, ignoring case. */
    @Query("select count(a) > 0 from OutdoorArea a where a.siteId = :siteId and lower(a.name) = lower(:name) and a.id <> :except")
    boolean nameTaken(UUID siteId, String name, UUID except);

    /** Inserts or updates an outdoor area. */
    OutdoorArea save(OutdoorArea area);
}
