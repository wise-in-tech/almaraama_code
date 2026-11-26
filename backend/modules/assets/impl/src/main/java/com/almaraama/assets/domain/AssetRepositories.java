package com.almaraama.assets.domain;

import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

/** Out ports of the module (G1 to G4). Nothing is deleted (M2). */
public final class AssetRepositories {

    private AssetRepositories() {
    }

    /** Families and models (G1). */
    public interface Families {

        /** Loads a family. */
        Optional<AssetFamily> findById(UUID id);

        /** Several families. */
        List<AssetFamily> findAllById(Collection<UUID> ids);

        /** The platform's families, and the organization's when given; by sort order then code. */
        List<AssetFamily> usable(Optional<OrganizationId> organization, boolean includeArchived);

        /** Whether a family visible to the organization (or a platform one when empty) already has this code. */
        boolean codeTaken(Optional<OrganizationId> organization, String code);

        /** Stores a family. */
        AssetFamily save(AssetFamily family);

        /** Loads a model. */
        Optional<AssetModel> findModel(UUID id);

        /** Several models. */
        List<AssetModel> findModels(Collection<UUID> ids);

        /** The models of families visible to the organization (platform ones only when empty). */
        List<AssetModel> modelsOf(Collection<UUID> familyIds, Optional<OrganizationId> organization,
                                  boolean includeArchived);

        /** Stores a model. */
        AssetModel save(AssetModel model);
    }

    /** Suppliers (G3). */
    public interface Suppliers {

        /** Loads a supplier. */
        Optional<Supplier> findById(UUID id);

        /** Several suppliers. */
        List<Supplier> findAllById(Collection<UUID> ids);

        /** The suppliers of an organization whose name or activity contains {@code query}, by name. */
        List<Supplier> ofOrganization(OrganizationId organization, String query, boolean includeInactive);

        /** Stores a supplier. */
        Supplier save(Supplier supplier);
    }

    /** Equipment, documents and warranties (G2 to G4). */
    public interface Assets {

        /** Search criteria of a site's equipment list. */
        record Search(SiteId siteId, UUID buildingId, UUID outdoorAreaId, UUID familyId, AssetState state,
                      String query, boolean includeArchived, Collection<UUID> visibleBuildings,
                      boolean allBuildings) {
        }

        /** Loads an equipment. */
        Optional<Asset> findById(UUID id);

        /** Several equipment. */
        List<Asset> findAllById(Collection<UUID> ids);

        /** The equipment with this QR code (G4), archived or not. */
        Optional<Asset> findByQrCode(String qrCode);

        /** One page of a site's equipment, by family then reference. */
        PageResult<Asset> search(Search search, PageQuery page);

        /** The references already used in a site. */
        List<String> referencesOf(SiteId siteId);

        /** Whether another equipment of the site has this reference. */
        boolean referenceTaken(SiteId siteId, String reference, Optional<UUID> except);

        /** Stores an equipment. */
        Asset save(Asset asset);

        /** The documents of an equipment, archived ones included, by date. */
        List<AssetDocument> documentsOf(UUID assetId);

        /** Stores a document. */
        AssetDocument save(AssetDocument document);

        /** The warranties of an equipment, by end date. */
        List<Warranty> warrantiesOf(UUID assetId);

        /** Stores a warranty. */
        Warranty save(Warranty warranty);
    }
}
