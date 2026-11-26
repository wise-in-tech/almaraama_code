package com.almaraama.assets.api;

import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** What other modules may ask of the {@code assets} module (CF-1). Callers check their own permissions. */
public interface AssetsApi {

    /** An equipment, archived or not. */
    Optional<AssetView> findAsset(UUID assetId);

    /** Several equipment; unknown identifiers are left out. */
    List<AssetView> findAssets(Collection<UUID> assetIds);
}
