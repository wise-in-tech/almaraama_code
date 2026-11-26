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

/** {@code assets.warranty}. */
interface SpringDataWarrantyTable extends Repository<Warranty, UUID> {

    /** The warranties of an equipment, by end date. */
    List<Warranty> findByAssetIdOrderByEndDateAsc(UUID assetId);

    /** Inserts or updates a warranty. */
    Warranty save(Warranty warranty);
}
