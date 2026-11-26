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

/** {@code assets.supplier}. */
interface SpringDataSupplierTable extends Repository<Supplier, UUID> {

    /** Loads a supplier. */
    Optional<Supplier> findById(UUID id);

    /** Loads several suppliers. */
    List<Supplier> findByIdIn(Collection<UUID> ids);

    /** The suppliers of an organization, by name. */
    @Query("select s from Supplier s where s.organizationId = :organization "
            + "and (:inactive = true or s.active = true) order by lower(s.name)")
    List<Supplier> ofOrganization(@Param("organization") UUID organization, @Param("inactive") boolean inactive);

    /** Inserts or updates a supplier. */
    Supplier save(Supplier supplier);
}
