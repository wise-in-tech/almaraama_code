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

/** {@code assets.asset}. */
interface SpringDataAssetTable extends Repository<Asset, UUID> {

    /** Loads an equipment. */
    Optional<Asset> findById(UUID id);

    /** Loads several equipment. */
    List<Asset> findByIdIn(Collection<UUID> ids);

    /** The equipment with this QR code. */
    Optional<Asset> findByQrCode(String qrCode);

    /** References used in a site. */
    @Query("select a.reference from Asset a where a.siteId = :site")
    List<String> referencesOf(@Param("site") UUID site);

    /** Whether another equipment of the site has the reference. */
    @Query("select count(a) > 0 from Asset a where a.siteId = :site and a.reference = :reference "
            + "and a.id <> :except")
    boolean referenceTaken(@Param("site") UUID site, @Param("reference") String reference,
                           @Param("except") UUID except);

    /** Inserts or updates an equipment. */
    Asset save(Asset asset);
}
