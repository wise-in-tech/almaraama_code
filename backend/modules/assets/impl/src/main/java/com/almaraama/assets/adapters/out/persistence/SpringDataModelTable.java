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

/** {@code assets.asset_model}. */
interface SpringDataModelTable extends Repository<AssetModel, UUID> {

    /** Loads a model. */
    Optional<AssetModel> findById(UUID id);

    /** Loads several models. */
    List<AssetModel> findByIdIn(Collection<UUID> ids);

    /** The platform's models of these families and the organization's. */
    @Query("select m from AssetModel m where m.familyId in :families "
            + "and (m.organizationId is null or m.organizationId = :organization) "
            + "and (:archived = true or m.active = true) order by m.name, m.modelVersion")
    List<AssetModel> usable(@Param("families") Collection<UUID> families,
                            @Param("organization") UUID organization, @Param("archived") boolean archived);

    /** The platform's models of these families only. */
    @Query("select m from AssetModel m where m.familyId in :families and m.organizationId is null "
            + "and (:archived = true or m.active = true) order by m.name, m.modelVersion")
    List<AssetModel> platform(@Param("families") Collection<UUID> families, @Param("archived") boolean archived);

    /** Inserts or updates a model. */
    AssetModel save(AssetModel model);
}
