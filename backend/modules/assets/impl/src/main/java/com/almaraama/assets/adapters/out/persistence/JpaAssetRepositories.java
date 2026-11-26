package com.almaraama.assets.adapters.out.persistence;







import com.almaraama.assets.domain.Asset;
import com.almaraama.assets.domain.AssetDocument;
import com.almaraama.assets.domain.AssetFamily;
import com.almaraama.assets.domain.AssetModel;
import com.almaraama.assets.domain.AssetRepositories;
import com.almaraama.assets.domain.Supplier;
import com.almaraama.assets.domain.Warranty;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import jakarta.persistence.EntityManager;
import jakarta.persistence.criteria.CriteriaBuilder;
import jakarta.persistence.criteria.CriteriaQuery;
import jakarta.persistence.criteria.Predicate;
import jakarta.persistence.criteria.Root;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

/** JPA adapters of the out ports of {@link AssetRepositories}. */
final class JpaAssetRepositories {

    /** An identifier no row has, to compare with {@code <>} when nothing is excluded. */
    private static final UUID NONE = new UUID(0, 0);

    private JpaAssetRepositories() {
    }

    /** {@link AssetRepositories.Families} on the family and model tables. */
    @Component
    static class Families implements AssetRepositories.Families {
        private final SpringDataFamilyTable familyTable;
        private final SpringDataModelTable modelTable;

        /**
         * @param familyTable families
         * @param modelTable  models
         */
        Families(SpringDataFamilyTable familyTable, SpringDataModelTable modelTable) {
            this.familyTable = familyTable;
            this.modelTable = modelTable;
        }

        /** Loads a family. */
        @Override
        public Optional<AssetFamily> findById(UUID id) {
            return familyTable.findById(id);
        }

        /** Loads several families; an empty input gives an empty list. */
        @Override
        public List<AssetFamily> findAllById(Collection<UUID> ids) {
            return ids.isEmpty() ? List.of() : familyTable.findByIdIn(ids);
        }

        /** The platform's families, and the organization's when given. */
        @Override
        public List<AssetFamily> usable(Optional<OrganizationId> organization, boolean includeArchived) {
            return organization.map(o -> familyTable.usable(o.value(), includeArchived))
                    .orElseGet(() -> familyTable.platform(includeArchived));
        }

        /** Whether a visible family already has the code. */
        @Override
        public boolean codeTaken(Optional<OrganizationId> organization, String code) {
            return usable(organization, true).stream().anyMatch(f -> f.code().equalsIgnoreCase(code));
        }

        /** Stores a family. */
        @Override
        public AssetFamily save(AssetFamily family) {
            return familyTable.save(family);
        }

        /** Loads a model. */
        @Override
        public Optional<AssetModel> findModel(UUID id) {
            return modelTable.findById(id);
        }

        /** Loads several models; an empty input gives an empty list. */
        @Override
        public List<AssetModel> findModels(Collection<UUID> ids) {
            return ids.isEmpty() ? List.of() : modelTable.findByIdIn(ids);
        }

        /** The models of the families visible to the organization. */
        @Override
        public List<AssetModel> modelsOf(Collection<UUID> familyIds, Optional<OrganizationId> organization,
                                         boolean includeArchived) {
            if (familyIds.isEmpty()) {
                return List.of();
            }
            return organization.map(o -> modelTable.usable(familyIds, o.value(), includeArchived))
                    .orElseGet(() -> modelTable.platform(familyIds, includeArchived));
        }

        /** Stores a model. */
        @Override
        public AssetModel save(AssetModel model) {
            return modelTable.save(model);
        }
    }

    /** {@link AssetRepositories.Suppliers} on the supplier table. */
    @Component
    static class Suppliers implements AssetRepositories.Suppliers {
        private final SpringDataSupplierTable table;

        /** @param table suppliers */
        Suppliers(SpringDataSupplierTable table) {
            this.table = table;
        }

        /** Loads a supplier. */
        @Override
        public Optional<Supplier> findById(UUID id) {
            return table.findById(id);
        }

        /** Loads several suppliers; an empty input gives an empty list. */
        @Override
        public List<Supplier> findAllById(Collection<UUID> ids) {
            return ids.isEmpty() ? List.of() : table.findByIdIn(ids);
        }

        /** The organization's suppliers whose name or activity contains the query (filtered in memory). */
        @Override
        public List<Supplier> ofOrganization(OrganizationId organization, String query, boolean includeInactive) {
            List<Supplier> all = table.ofOrganization(organization.value(), includeInactive);
            if (query == null || query.isBlank()) {
                return all;
            }
            String part = query.strip().toLowerCase(Locale.ROOT);
            return all.stream().filter(s -> s.name().toLowerCase(Locale.ROOT).contains(part)
                    || s.activity().map(a -> a.toLowerCase(Locale.ROOT).contains(part)).orElse(false)).toList();
        }

        /** Stores a supplier. */
        @Override
        public Supplier save(Supplier supplier) {
            return table.save(supplier);
        }
    }

    /** {@link AssetRepositories.Assets} on the equipment, document and warranty tables. */
    @Component
    static class Assets implements AssetRepositories.Assets {
        private final SpringDataAssetTable assetTable;
        private final SpringDataDocumentTable documentTable;
        private final SpringDataWarrantyTable warrantyTable;
        private final EntityManager entityManager;

        /**
         * @param assetTable    equipment
         * @param documentTable documents
         * @param warrantyTable warranties
         * @param entityManager criteria searches
         */
        Assets(SpringDataAssetTable assetTable, SpringDataDocumentTable documentTable, SpringDataWarrantyTable warrantyTable,
               EntityManager entityManager) {
            this.assetTable = assetTable;
            this.documentTable = documentTable;
            this.warrantyTable = warrantyTable;
            this.entityManager = entityManager;
        }

        /** Loads an equipment. */
        @Override
        public Optional<Asset> findById(UUID id) {
            return assetTable.findById(id);
        }

        /** Loads several equipment; an empty input gives an empty list. */
        @Override
        public List<Asset> findAllById(Collection<UUID> ids) {
            return ids.isEmpty() ? List.of() : assetTable.findByIdIn(ids);
        }

        /** The equipment with this QR code. */
        @Override
        public Optional<Asset> findByQrCode(String qrCode) {
            return assetTable.findByQrCode(qrCode);
        }

        /** One page by family then reference, built with the Criteria API (bound parameters only). */
        @Override
        public PageResult<Asset> search(Search search, PageQuery page) {
            CriteriaBuilder cb = entityManager.getCriteriaBuilder();
            CriteriaQuery<Asset> query = cb.createQuery(Asset.class);
            Root<Asset> asset = query.from(Asset.class);
            query.select(asset).where(predicates(cb, asset, search))
                    .orderBy(cb.asc(asset.get("familyId")), cb.asc(cb.lower(asset.get("reference"))));
            List<Asset> items = entityManager.createQuery(query).setFirstResult((int) page.offset())
                    .setMaxResults(page.size()).getResultList();
            CriteriaQuery<Long> count = cb.createQuery(Long.class);
            Root<Asset> counted = count.from(Asset.class);
            count.select(cb.count(counted)).where(predicates(cb, counted, search));
            return new PageResult<>(items, page.page(), page.size(), entityManager.createQuery(count)
                    .getSingleResult());
        }

        /** The conditions of the search. */
        private static Predicate[] predicates(CriteriaBuilder cb, Root<Asset> asset, Search search) {
            List<Predicate> where = new ArrayList<>();
            where.add(cb.equal(asset.get("siteId"), search.siteId().value()));
            if (!search.includeArchived()) {
                where.add(cb.isNull(asset.get("archivedAt")));
            }
            if (search.buildingId() != null) {
                where.add(cb.equal(asset.get("buildingId"), search.buildingId()));
            }
            if (search.outdoorAreaId() != null) {
                where.add(cb.equal(asset.get("outdoorAreaId"), search.outdoorAreaId()));
            }
            if (search.familyId() != null) {
                where.add(cb.equal(asset.get("familyId"), search.familyId()));
            }
            if (search.state() != null) {
                where.add(cb.equal(asset.get("state"), search.state()));
            }
            if (search.query() != null && !search.query().isBlank()) {
                String like = "%" + search.query().strip().toLowerCase(Locale.ROOT).replace("\\", "\\\\")
                        .replace("%", "\\%").replace("_", "\\_") + "%";
                where.add(cb.or(cb.like(cb.lower(asset.get("name")), like, '\\'),
                        cb.like(cb.lower(asset.get("reference")), like, '\\')));
            }
            if (!search.allBuildings()) {
                where.add(search.visibleBuildings().isEmpty() ? cb.isNull(asset.get("buildingId"))
                        : cb.or(cb.isNull(asset.get("buildingId")),
                        asset.get("buildingId").in(List.copyOf(search.visibleBuildings()))));
            }
            return where.toArray(Predicate[]::new);
        }

        /** References used in the site. */
        @Override
        public List<String> referencesOf(SiteId siteId) {
            return assetTable.referencesOf(siteId.value());
        }

        /** Whether another equipment of the site has the reference. */
        @Override
        public boolean referenceTaken(SiteId siteId, String reference, Optional<UUID> except) {
            return assetTable.referenceTaken(siteId.value(), reference, except.orElse(NONE));
        }

        /** Stores an equipment. */
        @Override
        public Asset save(Asset asset) {
            return assetTable.save(asset);
        }

        /** The documents of an equipment. */
        @Override
        public List<AssetDocument> documentsOf(UUID assetId) {
            return documentTable.findByAssetIdOrderByAddedAtAsc(assetId);
        }

        /** Stores a document. */
        @Override
        public AssetDocument save(AssetDocument document) {
            return documentTable.save(document);
        }

        /** The warranties of an equipment. */
        @Override
        public List<Warranty> warrantiesOf(UUID assetId) {
            return warrantyTable.findByAssetIdOrderByEndDateAsc(assetId);
        }

        /** Stores a warranty. */
        @Override
        public Warranty save(Warranty warranty) {
            return warrantyTable.save(warranty);
        }
    }
}
