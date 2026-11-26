package com.almaraama.assets.application;

import com.almaraama.assets.api.AssetView;
import com.almaraama.assets.api.AssetsApi;
import com.almaraama.assets.api.events.AssetArchivedV1;
import com.almaraama.assets.api.events.AssetCreatedV1;
import com.almaraama.assets.api.events.AssetQrRotatedV1;
import com.almaraama.assets.api.events.AssetUpdatedV1;
import com.almaraama.assets.domain.Asset;
import com.almaraama.assets.domain.AssetDocument;
import com.almaraama.assets.domain.AssetDocumentType;
import com.almaraama.assets.domain.AssetFamily;
import com.almaraama.assets.domain.AssetModel;
import com.almaraama.assets.domain.AssetRepositories.Assets;
import com.almaraama.assets.domain.AssetRepositories.Families;
import com.almaraama.assets.domain.AssetRepositories.Suppliers;
import com.almaraama.assets.domain.AssetState;
import com.almaraama.assets.domain.QrCodes;
import com.almaraama.assets.domain.Supplier;
import com.almaraama.assets.domain.Warranty;
import com.almaraama.customization.api.AttributesApi;
import com.almaraama.files.api.FilePurpose;
import com.almaraama.files.api.FilesApi;
import com.almaraama.files.api.SignedLink;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.id.BuildingId;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import com.almaraama.shared.paging.PageQuery;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.shared.perimeter.Perimeter;
import com.almaraama.sites.api.BuildingSummary;
import com.almaraama.sites.api.OutdoorAreaSummary;
import com.almaraama.sites.api.SiteSummary;
import com.almaraama.sites.api.SitesApi;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

/**
 * Equipment of the sites (G1 to G4, C4, ADR-039): list, card, creation and changes, archive, QR code, documents and
 * warranties. Residents see the resident view ({@code asset:read}); the management view (supplier, documents,
 * warranties, characteristics, QR code) needs {@code asset:update}.
 */
@Service
public class AssetService implements AssetsApi {

    private static final Logger LOG = LoggerFactory.getLogger(AssetService.class);

    /** What a person types for an equipment; {@code familyId} is used on creation only. */
    public record AssetCommand(UUID familyId, Optional<UUID> modelId, String name, Optional<String> reference,
                               Optional<UUID> buildingId, Optional<Integer> floor, Optional<UUID> outdoorAreaId,
                               AssetState state, String serialNumber, Optional<LocalDate> commissionedOn,
                               Optional<UUID> supplierId, Map<String, Object> attributes) {
    }

    /** An equipment with its labels, as listed. {@code management} says whether the reader holds {@code asset:update}. */
    public record AssetCard(Asset asset, String siteName, AssetFamily family, Optional<AssetModel> model,
                            Optional<BuildingSummary> building, Optional<OutdoorAreaSummary> outdoorArea,
                            boolean management) {
    }

    /** A document with a link valid 5 minutes. */
    public record DocumentView(AssetDocument document, SignedLink link) {
    }

    /** The whole equipment; the management parts are empty without {@code asset:update}. */
    public record AssetDetail(AssetCard card, Optional<Supplier> supplier, Map<String, Object> attributes,
                              List<DocumentView> documents, List<Warranty> warranties) {
    }

    /** What a person types for a warranty. */
    public record WarrantyCommand(LocalDate start, LocalDate end, int alertDaysBefore, Optional<UUID> documentId,
                                  String note) {
    }

    /** Search criteria of a site's list. */
    public record ListFilter(UUID buildingId, UUID outdoorAreaId, UUID familyId, AssetState state, String query,
                             boolean includeArchived) {
    }

    private final Assets assets;
    private final Families families;
    private final Suppliers suppliers;
    private final SitesApi sites;
    private final AttributesApi attributes;
    private final FilesApi files;
    private final ApplicationEventPublisher events;
    private final Clock clock;

    /**
     * @param assets     equipment, documents, warranties
     * @param families   families and models
     * @param suppliers  supplier contacts
     * @param sites      sites, buildings and outdoor areas
     * @param attributes characteristic definitions
     * @param files      stored files
     * @param events     publication of the module's events
     * @param clock      time of changes
     */
    AssetService(Assets assets, Families families, Suppliers suppliers, SitesApi sites, AttributesApi attributes,
                 FilesApi files, ApplicationEventPublisher events, Clock clock) {
        this.assets = assets;
        this.families = families;
        this.suppliers = suppliers;
        this.sites = sites;
        this.attributes = attributes;
        this.files = files;
        this.events = events;
        this.clock = clock;
    }

    // ------------------------------------------------------------------------------------------------- reading

    /**
     * One page of a site's equipment; needs {@code asset:read} on the site. A resident restricted to some buildings
     * (C7) sees their equipment and the equipment outside any building.
     */
    @Transactional(readOnly = true)
    public PageResult<AssetCard> list(Perimeter perimeter, SiteId siteId, ListFilter filter, PageQuery page) {
        SiteSummary site = site(siteId);
        perimeter.require(Permission.ASSET_READ, site.organizationId(), siteId);
        Set<UUID> visible = perimeter.site(siteId).map(a -> a.allowedBuildingIds().stream().map(BuildingId::value)
                .collect(Collectors.toSet())).orElse(Set.of());
        boolean all = allBuildings(perimeter, site);
        PageResult<Asset> found = assets.search(new Assets.Search(siteId, filter.buildingId(), filter.outdoorAreaId(),
                filter.familyId(), filter.state(), filter.query(), filter.includeArchived(), visible, all), page);
        boolean management = perimeter.can(Permission.ASSET_UPDATE, site.organizationId(), siteId);
        List<AssetCard> cards = cards(found.items(), site, management);
        return new PageResult<>(cards, found.page(), found.size(), found.totalElements());
    }

    /** An equipment; needs {@code asset:read} on its site and its building visible (C7); else {@code NOT_FOUND}. */
    @Transactional(readOnly = true)
    public AssetDetail get(Perimeter perimeter, UUID assetId) {
        Asset asset = visible(perimeter, assetId);
        return detail(perimeter, asset);
    }

    /**
     * G4, criterion 8: the equipment behind a QR code. {@code NOT_FOUND} alike for a malformed, unknown or replaced
     * code, an archived equipment and an equipment the reader may not see.
     */
    @Transactional(readOnly = true)
    public AssetCard resolveQr(Perimeter perimeter, String code) {
        if (!QrCodes.wellFormed(code)) {
            throw Errors.notFound();
        }
        Asset asset = assets.findByQrCode(code).filter(a -> !a.archived()).orElseThrow(Errors::notFound);
        if (!readable(perimeter, asset)) {
            throw Errors.notFound();
        }
        SiteSummary site = site(asset.siteId());
        LOG.debug("QR code resolved to equipment {}", asset.id());
        return cards(List.of(asset), site, perimeter.can(Permission.ASSET_UPDATE, site.organizationId(),
                asset.siteId())).getFirst();
    }

    // ----------------------------------------------------------------------------------------------- changes

    /**
     * Adds an equipment to a site; needs {@code asset:create} there. Checks the family, model, location, supplier,
     * reference and characteristics (see {@link #prepare}); {@code ASSET_REFERENCE_TAKEN}.
     */
    @Transactional
    public AssetDetail create(Perimeter perimeter, SiteId siteId, AssetCommand command) {
        SiteSummary site = site(siteId);
        perimeter.require(Permission.ASSET_CREATE, site.organizationId(), siteId);
        Instant now = clock.instant();
        Asset asset = assets.save(Asset.create(site.organizationId(), siteId, command.familyId(),
                prepare(site, command.familyId(), command, Optional.empty(), new HashSet<>(assets.referencesOf(siteId))),
                now));
        events.publishEvent(new AssetCreatedV1(asset.id(), site.organizationId(), siteId, asset.name(),
                perimeter.actor(), now));
        LOG.debug("Equipment {} created on site {}", asset.id(), siteId.value());
        return detail(perimeter, asset);
    }

    /** Changes an equipment; needs {@code asset:update} on its site. Same checks; {@code VERSION_CONFLICT}. */
    @Transactional
    public AssetDetail update(Perimeter perimeter, UUID assetId, AssetCommand command, long version) {
        Asset asset = manageable(perimeter, assetId, Permission.ASSET_UPDATE);
        Errors.checkVersion(version, asset.version());
        SiteSummary site = site(asset.siteId());
        Instant now = clock.instant();
        asset.update(prepare(site, asset.familyId(), command, Optional.of(asset), new HashSet<>()), now);
        assets.save(asset);
        events.publishEvent(new AssetUpdatedV1(asset.id(), asset.organizationId(), asset.siteId(), asset.name(),
                perimeter.actor(), now));
        return detail(perimeter, asset);
    }

    /** Archives an equipment; needs {@code asset:archive} on its site (M2). {@code ASSET_ARCHIVED}, {@code VERSION_CONFLICT}. */
    @Transactional
    public AssetDetail archive(Perimeter perimeter, UUID assetId, long version) {
        Asset asset = manageable(perimeter, assetId, Permission.ASSET_ARCHIVE);
        Errors.checkVersion(version, asset.version());
        Instant now = clock.instant();
        asset.archive(now);
        assets.save(asset);
        events.publishEvent(new AssetArchivedV1(asset.id(), asset.organizationId(), asset.siteId(), asset.name(),
                perimeter.actor(), now));
        return detail(perimeter, asset);
    }

    /** Replaces the QR code (G4); needs {@code asset:update}. The old code stops answering at once. */
    @Transactional
    public AssetDetail rotateQr(Perimeter perimeter, UUID assetId) {
        Asset asset = manageable(perimeter, assetId, Permission.ASSET_UPDATE);
        Instant now = clock.instant();
        asset.rotateQrCode(now);
        assets.save(asset);
        events.publishEvent(new AssetQrRotatedV1(asset.id(), asset.organizationId(), asset.siteId(), asset.name(),
                perimeter.actor(), now));
        return detail(perimeter, asset);
    }

    /**
     * Attaches a document (G3); needs {@code asset:update}. The file must have been uploaded by the caller for {@code
     * ASSET_DOCUMENT} ({@code FILE_NOT_USABLE}).
     */
    @Transactional
    public AssetDetail addDocument(Perimeter perimeter, UUID assetId, UUID fileId, AssetDocumentType type,
                                   String title) {
        Asset asset = manageable(perimeter, assetId, Permission.ASSET_UPDATE);
        Instant now = clock.instant();
        AssetDocument document = AssetDocument.attach(asset.id(), fileId, type, title, perimeter.actor().id(), now);
        files.attach(fileId, perimeter.actor(), FilePurpose.ASSET_DOCUMENT, "assets", asset.id());
        assets.save(document);
        asset.touch(now);
        assets.save(asset);
        return detail(perimeter, asset);
    }

    /** Hides a document (M2); needs {@code asset:update}. */
    @Transactional
    public AssetDetail archiveDocument(Perimeter perimeter, UUID assetId, UUID documentId) {
        Asset asset = manageable(perimeter, assetId, Permission.ASSET_UPDATE);
        AssetDocument document = assets.documentsOf(asset.id()).stream().filter(d -> d.id().equals(documentId))
                .findFirst().orElseThrow(Errors::notFound);
        Instant now = clock.instant();
        document.archive(now);
        assets.save(document);
        asset.touch(now);
        assets.save(asset);
        return detail(perimeter, asset);
    }

    /** Records a warranty (G3); needs {@code asset:update}. {@code WARRANTY_DATES_INVALID}. */
    @Transactional
    public AssetDetail addWarranty(Perimeter perimeter, UUID assetId, WarrantyCommand command) {
        Asset asset = manageable(perimeter, assetId, Permission.ASSET_UPDATE);
        checkDocument(asset, command.documentId());
        Instant now = clock.instant();
        assets.save(Warranty.create(asset.id(), command.start(), command.end(), command.alertDaysBefore(),
                command.documentId(), command.note(), now));
        asset.touch(now);
        assets.save(asset);
        return detail(perimeter, asset);
    }

    /** Corrects or archives a warranty; needs {@code asset:update}. {@code VERSION_CONFLICT}. */
    @Transactional
    public AssetDetail updateWarranty(Perimeter perimeter, UUID assetId, UUID warrantyId, WarrantyCommand command,
                                      boolean active, long version) {
        Asset asset = manageable(perimeter, assetId, Permission.ASSET_UPDATE);
        Warranty warranty = assets.warrantiesOf(asset.id()).stream().filter(w -> w.id().equals(warrantyId))
                .findFirst().orElseThrow(Errors::notFound);
        Errors.checkVersion(version, warranty.version());
        checkDocument(asset, command.documentId());
        Instant now = clock.instant();
        warranty.update(command.start(), command.end(), command.alertDaysBefore(), command.documentId(),
                command.note(), active, now);
        assets.save(warranty);
        asset.touch(now);
        assets.save(asset);
        return detail(perimeter, asset);
    }

    // ------------------------------------------------------------------------------------------ AssetsApi

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public Optional<AssetView> findAsset(UUID assetId) {
        return assets.findById(assetId).map(AssetService::view);
    }

    /** {@inheritDoc} */
    @Override
    @Transactional(readOnly = true)
    public List<AssetView> findAssets(Collection<UUID> assetIds) {
        return assets.findAllById(assetIds).stream().map(AssetService::view).toList();
    }

    // ------------------------------------------------------------------------------------------- internals

    /**
     * The details of an equipment after every check against its site (shared with the Excel import): family usable by
     * the organization and active ({@code ASSET_FAMILY_NOT_IN_SCOPE}, {@code ASSET_FAMILY_ARCHIVED}); model of the
     * family ({@code ASSET_MODEL_FAMILY_MISMATCH}, {@code ASSET_MODEL_ARCHIVED}); building and outdoor area of the site
     * ({@code BUILDING_NOT_IN_SITE}, {@code OUTDOOR_AREA_NOT_IN_SITE}); supplier of the organization ({@code
     * SUPPLIER_NOT_IN_ORGANIZATION}); reference free in the site, generated when absent ({@code
     * ASSET_REFERENCE_TAKEN}); characteristics ({@code ATTRIBUTE_INVALID}).
     *
     * @param current  the equipment being changed, if any
     * @param taken    references already used, completed with the generated one (creation and import)
     */
    Asset.Details prepare(SiteSummary site, UUID familyId, AssetCommand command, Optional<Asset> current,
                          Set<String> taken) {
        OrganizationId organization = site.organizationId();
        AssetFamily family = families.findById(familyId).filter(f -> f.usableBy(organization))
                .orElseThrow(() -> Errors.rule("ASSET_FAMILY_NOT_IN_SCOPE"));
        if (!family.active() && current.isEmpty()) {
            throw Errors.rule("ASSET_FAMILY_ARCHIVED");
        }
        command.modelId().ifPresent(modelId -> {
            AssetModel model = families.findModel(modelId).filter(m -> m.usableBy(organization))
                    .orElseThrow(() -> Errors.rule("ASSET_MODEL_FAMILY_MISMATCH"));
            if (!model.familyId().equals(familyId)) {
                throw Errors.rule("ASSET_MODEL_FAMILY_MISMATCH");
            }
            boolean unchanged = current.flatMap(Asset::modelId).filter(modelId::equals).isPresent();
            if (!model.active() && !unchanged) {
                throw Errors.rule("ASSET_MODEL_ARCHIVED");
            }
        });
        command.buildingId().ifPresent(id -> sites.findBuilding(new BuildingId(id))
                .filter(b -> b.siteId().equals(site.id()) && b.active())
                .orElseThrow(() -> Errors.rule("BUILDING_NOT_IN_SITE")));
        command.outdoorAreaId().ifPresent(id -> sites.findOutdoorArea(id)
                .filter(a -> a.siteId().equals(site.id()) && a.active())
                .orElseThrow(() -> Errors.rule("OUTDOOR_AREA_NOT_IN_SITE")));
        command.supplierId().ifPresent(id -> suppliers.findById(id)
                .filter(s -> s.organizationId().equals(organization) && s.active())
                .orElseThrow(() -> Errors.rule("SUPPLIER_NOT_IN_ORGANIZATION")));
        String reference = command.reference().map(String::strip).filter(r -> !r.isEmpty())
                .orElseGet(() -> current.map(Asset::reference).orElseGet(() -> nextReference(family, taken)));
        if (taken.contains(reference)
                || assets.referenceTaken(site.id(), reference, current.map(Asset::id))) {
            throw new BusinessException("ASSET_REFERENCE_TAKEN", ErrorCategory.CONFLICT, "Reference used");
        }
        taken.add(reference);
        Map<String, Object> values = attributes.validate(familyId, organization.value(),
                command.attributes() == null ? Map.of() : command.attributes());
        return new Asset.Details(command.modelId(), command.name(), reference,
                new Asset.Location(command.buildingId().map(BuildingId::new), command.floor(),
                        command.outdoorAreaId()), command.state(), command.serialNumber(), command.commissionedOn(),
                command.supplierId(), AttributeJson.write(values));
    }

    /** The first free reference {@code <FAMILY>-001}, {@code -002}… */
    private static String nextReference(AssetFamily family, Set<String> taken) {
        String prefix = family.code().length() > 30 ? family.code().substring(0, 30) : family.code();
        for (int n = 1; ; n++) {
            String candidate = prefix + "-" + String.format("%03d", n);
            if (!taken.contains(candidate)) {
                return candidate;
            }
        }
    }

    /** A warranty's document must be one of the equipment's ({@code ASSET_DOCUMENT_UNKNOWN}). */
    private void checkDocument(Asset asset, Optional<UUID> documentId) {
        documentId.ifPresent(id -> {
            if (assets.documentsOf(asset.id()).stream().noneMatch(d -> d.id().equals(id))) {
                throw Errors.rule("ASSET_DOCUMENT_UNKNOWN");
            }
        });
    }

    /** The equipment when the reader may see it, else {@code NOT_FOUND}. */
    private Asset visible(Perimeter perimeter, UUID assetId) {
        Asset asset = assets.findById(assetId).orElseThrow(Errors::notFound);
        if (!readable(perimeter, asset)) {
            throw Errors.notFound();
        }
        return asset;
    }

    /** The equipment when visible and the reader holds the permission on its site; 404 or 403 otherwise. */
    private Asset manageable(Perimeter perimeter, UUID assetId, Permission permission) {
        Asset asset = assets.findById(assetId).orElseThrow(Errors::notFound);
        perimeter.require(permission, asset.organizationId(), asset.siteId());
        return asset;
    }

    /** {@code asset:read} on the site and the building visible (C7). */
    private static boolean readable(Perimeter perimeter, Asset asset) {
        return perimeter.can(Permission.ASSET_READ, asset.organizationId(), asset.siteId())
                && asset.location().buildingId().map(b -> perimeter.canSeeBuilding(asset.organizationId(),
                asset.siteId(), b)).orElse(true);
    }

    /** Whether the reader sees every building of the site (no C7 restriction). */
    private static boolean allBuildings(Perimeter perimeter, SiteSummary site) {
        return perimeter.seesWholeOrganization(site.organizationId())
                || perimeter.site(site.id()).map(a -> a.permissions().contains(Permission.UNIT_READ)
                || a.allowedBuildingIds().isEmpty()).orElse(true);
    }

    /** The site, or {@code NOT_FOUND}. */
    private SiteSummary site(SiteId siteId) {
        return sites.findSite(siteId).orElseThrow(Errors::notFound);
    }

    /** Cards of a page, with their labels loaded in a few queries. */
    private List<AssetCard> cards(List<Asset> list, SiteSummary site, boolean management) {
        Map<UUID, AssetFamily> familyById = families.findAllById(list.stream().map(Asset::familyId).distinct()
                .toList()).stream().collect(Collectors.toMap(AssetFamily::id, Function.identity()));
        Map<UUID, AssetModel> modelById = families.findModels(list.stream().map(Asset::modelId)
                .flatMap(Optional::stream).distinct().toList()).stream()
                .collect(Collectors.toMap(AssetModel::id, Function.identity()));
        Map<BuildingId, BuildingSummary> buildingById = sites.findBuildings(list.stream()
                .map(a -> a.location().buildingId()).flatMap(Optional::stream).distinct().toList()).stream()
                .collect(Collectors.toMap(BuildingSummary::id, Function.identity()));
        Map<UUID, OutdoorAreaSummary> areaById = sites.findOutdoorAreas(list.stream()
                .map(a -> a.location().outdoorAreaId()).flatMap(Optional::stream).distinct().toList()).stream()
                .collect(Collectors.toMap(OutdoorAreaSummary::id, Function.identity()));
        return list.stream().map(a -> new AssetCard(a, site.name(), familyById.get(a.familyId()),
                a.modelId().map(modelById::get), a.location().buildingId().map(buildingById::get),
                a.location().outdoorAreaId().map(areaById::get), management)).toList();
    }

    /** The whole equipment as this reader may see it. */
    private AssetDetail detail(Perimeter perimeter, Asset asset) {
        SiteSummary site = site(asset.siteId());
        boolean management = perimeter.can(Permission.ASSET_UPDATE, site.organizationId(), asset.siteId());
        AssetCard card = cards(List.of(asset), site, management).getFirst();
        if (!management) {
            return new AssetDetail(card, Optional.empty(), Map.of(), List.of(), List.of());
        }
        List<AssetDocument> documents = assets.documentsOf(asset.id()).stream().filter(d -> !d.archived()).toList();
        Map<UUID, SignedLink> links = files.signedLinks(documents.stream().map(AssetDocument::fileId).toList());
        List<DocumentView> views = documents.stream().filter(d -> links.containsKey(d.fileId()))
                .map(d -> new DocumentView(d, links.get(d.fileId()))).toList();
        return new AssetDetail(card, asset.supplierId().flatMap(suppliers::findById),
                AttributeJson.read(asset.attributesJson()), views,
                assets.warrantiesOf(asset.id()).stream().filter(Warranty::active).toList());
    }

    /** The equipment as other modules see it. */
    private static AssetView view(Asset a) {
        Asset.Location location = a.location();
        return new AssetView(a.id(), a.organizationId(), a.siteId(), a.familyId(), a.name(), a.reference(),
                location.buildingId(), location.floor(), location.outdoorAreaId(), a.archived());
    }
}
