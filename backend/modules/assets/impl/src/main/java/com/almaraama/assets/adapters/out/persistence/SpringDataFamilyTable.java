package com.almaraama.assets.adapters.out.persistence;

import com.almaraama.assets.domain.Asset;
import com.almaraama.assets.domain.AssetDocument;
import com.almaraama.assets.domain.AssetFamily;
import com.almaraama.assets.domain.AssetModel;
import com.almaraama.assets.domain.Supplier;
import com.almaraama.assets.domain.Warranty;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.Repository;
import org.springframework.data.repository.query.Param;

/** {@code assets.asset_family}. */
interface SpringDataFamilyTable extends Repository<AssetFamily, UUID> {

    /** Loads a family. */
    Optional<AssetFamily> findById(UUID id);

    /** Loads several families. */
    List<AssetFamily> findByIdIn(Collection<UUID> ids);

    /** The platform's families and the organization's. */
    @Query("select f from AssetFamily f where (f.organizationId is null or f.organizationId = :organization) "
            + "and (:archived = true or f.active = true) order by f.sortOrder, f.code")
    List<AssetFamily> usable(@Param("organization") UUID organization, @Param("archived") boolean archived);

    /** The platform's families only. */
    @Query("select f from AssetFamily f where f.organizationId is null "
            + "and (:archived = true or f.active = true) order by f.sortOrder, f.code")
    List<AssetFamily> platform(@Param("archived") boolean archived);

    /** Inserts or updates a family. */
    AssetFamily save(AssetFamily family);
}
