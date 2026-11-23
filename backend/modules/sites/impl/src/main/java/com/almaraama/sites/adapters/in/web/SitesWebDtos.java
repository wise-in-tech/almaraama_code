package com.almaraama.sites.adapters.in.web;

import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.sites.api.OrganizationType;
import com.almaraama.sites.api.UnitType;
import com.almaraama.sites.application.Views.AddressView;
import com.almaraama.sites.application.Views.BuildingView;
import com.almaraama.sites.application.Views.SiteView;
import com.almaraama.sites.application.Views.UnitView;
import com.almaraama.sites.application.Views.ZoneView;
import com.almaraama.sites.application.ZoneAdministration;
import com.almaraama.sites.domain.Address;
import com.almaraama.sites.domain.AddressOrigin;
import com.almaraama.sites.domain.BuildingUse;
import com.almaraama.sites.domain.Country;
import com.almaraama.sites.domain.LocalizedName;
import com.almaraama.sites.domain.Organization;
import com.almaraama.sites.domain.OutdoorArea;
import com.almaraama.sites.domain.OutdoorAreaType;
import com.almaraama.sites.domain.SiteLifecycleStatus;
import com.almaraama.sites.domain.SiteType;
import com.almaraama.sites.domain.Unit;
import com.almaraama.sites.domain.UnitCategory;
import com.almaraama.sites.domain.Zone;
import com.almaraama.sites.domain.ZoneStatus;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.DecimalMax;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.util.List;
import java.util.UUID;
import java.util.function.Function;

/** Request and response bodies of the sites endpoints, exactly as in api/openapi.yaml (v0.4.0). Validated on entry. */
final class SitesWebDtos {

    /** Holder of the request and response records only. */
    private SitesWebDtos() {
    }

    // ------------------------------------------------------------------------------------------------ shared

    /** A text in the three interface languages (contract {@code LocalizedText}, M1). */
    record LocalizedText(String ar, String en, String fr) {
        /** From a domain name in three languages. */
        static LocalizedText of(LocalizedName name) {
            return new LocalizedText(name.ar(), name.en(), name.fr());
        }
    }

    /** A text typed in one or more languages (contract {@code LocalizedTextInput}); at least one is checked later. */
    record LocalizedTextInput(@Size(max = 100) String ar, @Size(max = 100) String en, @Size(max = 100) String fr) {
        /** To a domain name; missing languages stay empty. */
        LocalizedName toName() {
            return new LocalizedName(ar, en, fr);
        }
    }

    /** Position of a page in the whole result (contract {@code PageMeta}). */
    record PageMeta(int page, int size, long totalElements, int totalPages) {
    }

    /** One page of response bodies (contract {@code *Page} schemas). */
    record Page<T>(List<T> items, PageMeta page) {
        /** A page of response bodies from a page of domain objects. */
        static <S, T> Page<T> of(PageResult<S> result, Function<S, T> mapper) {
            return new Page<>(result.items().stream().map(mapper).toList(), new PageMeta(result.page(),
                    result.size(), result.totalElements(), result.totalPages()));
        }
    }

    /** A scope (contract {@code Scope}): platform, organization or site. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ScopeBody(@NotNull ScopeType type, UUID id) {
    }

    // ------------------------------------------------------------------------------------------ reference data

    /** A named level of a country (contract {@code Country.levels}). */
    record CountryLevel(int level, LocalizedText name) {
    }

    /** A country with its parameters (contract {@code Country}, A8). */
    record CountryResponse(String code, LocalizedText name, List<CountryLevel> levels, int attachmentLevel,
                           String currencyCode, String timeZone, List<String> languages, String callingCode,
                           List<String> workingDays) {
        /** From the reference entity. */
        static CountryResponse of(Country c) {
            return new CountryResponse(c.code(), LocalizedText.of(c.name()), c.levels().stream()
                    .map(l -> new CountryLevel(l.level(), LocalizedText.of(l.name()))).toList(), c.attachmentLevel(),
                    c.currencyCode(), c.timeZone(), c.languages(), c.callingCode(), c.workingDays());
        }
    }

    /** A zone without its ancestors (contract {@code ZoneRef}). */
    record ZoneRef(UUID id, int level, String code, LocalizedText name) {
        /** From the entity. */
        static ZoneRef of(Zone z) {
            return new ZoneRef(z.id(), z.level(), z.code(), LocalizedText.of(z.name()));
        }
    }

    /** A zone with its ancestors (contract {@code Zone}, A8). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ZoneResponse(UUID id, int level, String code, LocalizedText name, String countryCode, UUID parentId,
                        ZoneStatus status, List<ZoneRef> ancestors) {
        /** From a zone and its ancestors. */
        static ZoneResponse of(ZoneView view) {
            Zone z = view.zone();
            return new ZoneResponse(z.id(), z.level(), z.code(), LocalizedText.of(z.name()), z.countryCode(),
                    z.parentId().orElse(null), z.status(), view.ancestors().stream().map(ZoneRef::of).toList());
        }
    }

    /** Body of {@code POST /v1/zones/provisional} (A8). */
    record ProvisionalZoneRequest(@NotNull UUID organizationId, UUID parentId,
                                  @NotNull @Valid LocalizedTextInput name) {
    }

    /** An address as sent by the client (contract {@code AddressInput}, A9). */
    record AddressInput(
            @Size(max = 100) String label,
            @NotNull UUID zoneId,
            @NotBlank @Size(max = 300) String addressLine,
            @Size(max = 20) String postalCode,
            @DecimalMin("-90") @DecimalMax("90") Double latitude,
            @DecimalMin("-180") @DecimalMax("180") Double longitude) {
        /** To the domain's address specification, which trims and checks the free text. */
        Address.Spec toSpec() {
            return new Address.Spec(label, zoneId, addressLine, postalCode, latitude, longitude);
        }
    }

    /** An address with its zone and ancestors (contract {@code Address}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AddressResponse(String label, UUID zoneId, String addressLine, String postalCode, Double latitude,
                           Double longitude, String countryCode, ZoneResponse zone) {
        /** From an address and its resolved zone. */
        static AddressResponse of(AddressView view) {
            Address a = view.address();
            return new AddressResponse(a.label().orElse(null), a.zoneId(), a.addressLine(),
                    a.postalCode().orElse(null), a.latitude().orElse(null), a.longitude().orElse(null),
                    view.zone().zone().countryCode(), ZoneResponse.of(view.zone()));
        }
    }

    // ------------------------------------------------------------------------------------------ organizations

    /** An organization (contract {@code Organization}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record OrganizationResponse(UUID id, OrganizationType type, String name, String countryCode,
                                String currencyCode, String contactPhone, String contactEmail, boolean active,
                                long version) {
        /** From the aggregate and the currency of its country (M5). */
        static OrganizationResponse of(Organization o, String currencyCode) {
            return new OrganizationResponse(o.id().value(), o.type(), o.name(), o.countryCode(), currencyCode,
                    o.contactPhone().orElse(null), o.contactEmail().orElse(null), o.active(), o.version());
        }
    }

    /** Body of {@code PUT /v1/organizations/{id}}, with the version read by the client. */
    record UpdateOrganizationRequest(
            @NotBlank @Size(max = 200) String name,
            @Size(max = 30) String contactPhone,
            @Email @Size(max = 254) String contactEmail,
            @NotNull @PositiveOrZero Long version) {
    }

    // ----------------------------------------------------------------------------------------------- sites

    /** Body of {@code POST /v1/organizations/{id}/sites} (A1, A5, A9). */
    record SiteInput(
            @NotNull SiteType type,
            @NotBlank @Size(max = 200) String name,
            @NotNull @Valid AddressInput address,
            @NotNull SiteLifecycleStatus lifecycleStatus) {
    }

    /** Body of {@code PUT /v1/sites/{id}}, with the version read by the client. */
    record UpdateSiteRequest(
            @NotBlank @Size(max = 200) String name,
            @NotNull @Valid AddressInput address,
            @NotNull SiteLifecycleStatus lifecycleStatus,
            @NotNull Boolean active,
            @NotNull @PositiveOrZero Long version) {
    }

    /** A site with its address (contract {@code Site}). */
    record SiteResponse(UUID id, UUID organizationId, SiteType type, String name, String countryCode,
                        AddressResponse address, SiteLifecycleStatus lifecycleStatus, boolean active, long version) {
        /** From a site and its address. */
        static SiteResponse of(SiteView view) {
            var s = view.site();
            return new SiteResponse(s.id().value(), s.organizationId().value(), s.type(), s.name(),
                    view.countryCode(), AddressResponse.of(view.address()), s.lifecycleStatus(), s.active(),
                    s.version());
        }
    }

    // ------------------------------------------------------------------------------------------- buildings

    /** Body of {@code POST /v1/sites/{id}/buildings}; without address, a copy of the site's address (A9). */
    record BuildingInput(
            @NotBlank @Size(max = 100) String name,
            @NotNull BuildingUse buildingUse,
            @NotNull @Min(0) @Max(200) Integer floorCount,
            @Valid AddressInput address) {
    }

    /** Body of {@code PUT /v1/buildings/{id}}; the address is always sent (A9). */
    record UpdateBuildingRequest(
            @NotBlank @Size(max = 100) String name,
            @NotNull BuildingUse buildingUse,
            @NotNull @Min(0) @Max(200) Integer floorCount,
            @NotNull @Valid AddressInput address,
            @NotNull Boolean active,
            @NotNull @PositiveOrZero Long version) {
    }

    /** A building with its address and where it comes from (contract {@code Building}, criterion 18). */
    record BuildingResponse(UUID id, UUID siteId, String name, BuildingUse buildingUse, int floorCount,
                            AddressResponse address, AddressOrigin addressOrigin, boolean active, long version) {
        /** From a building and its address. */
        static BuildingResponse of(BuildingView view) {
            var b = view.building();
            return new BuildingResponse(b.id().value(), b.siteId().value(), b.name(), b.buildingUse(),
                    b.floorCount(), AddressResponse.of(view.address()), view.address().address().origin(),
                    b.active(), b.version());
        }
    }

    // ---------------------------------------------------------------------------------------- outdoor areas

    /** Body of {@code POST /v1/sites/{id}/outdoor-areas} (A3). */
    record OutdoorAreaInput(@NotNull OutdoorAreaType type, @NotBlank @Size(max = 100) String name) {
    }

    /** Body of {@code PUT /v1/outdoor-areas/{id}}, with the version read by the client. */
    record UpdateOutdoorAreaRequest(
            @NotNull OutdoorAreaType type,
            @NotBlank @Size(max = 100) String name,
            @NotNull Boolean active,
            @NotNull @PositiveOrZero Long version) {
    }

    /** An outdoor area (contract {@code OutdoorArea}). */
    record OutdoorAreaResponse(UUID id, UUID siteId, OutdoorAreaType type, String name, boolean active,
                               long version) {
        /** From the aggregate. */
        static OutdoorAreaResponse of(OutdoorArea a) {
            return new OutdoorAreaResponse(a.id(), a.siteId().value(), a.type(), a.name(), a.active(), a.version());
        }
    }

    // ------------------------------------------------------------------------------------------------ units

    /** Body of {@code POST /v1/sites/{id}/units} (A6, A7). */
    record UnitInput(
            @NotNull UnitType type,
            @NotNull UUID categoryId,
            @NotBlank @Size(max = 50) String reference,
            UUID buildingId,
            UUID outdoorAreaId,
            @Min(-10) @Max(200) Integer floor,
            UUID annexOfUnitId,
            @NotNull Boolean saleable,
            @NotNull Boolean rentable) {
        /** To the domain's unit specification. */
        Unit.Spec toSpec() {
            return new Unit.Spec(type, categoryId, reference, buildingId, outdoorAreaId, floor, annexOfUnitId,
                    saleable, rentable);
        }
    }

    /** Body of {@code PUT /v1/units/{id}}, with the version read by the client. */
    record UpdateUnitRequest(
            @NotNull UnitType type,
            @NotNull UUID categoryId,
            @NotBlank @Size(max = 50) String reference,
            UUID buildingId,
            UUID outdoorAreaId,
            @Min(-10) @Max(200) Integer floor,
            UUID annexOfUnitId,
            @NotNull Boolean saleable,
            @NotNull Boolean rentable,
            @NotNull Boolean active,
            @NotNull @PositiveOrZero Long version) {
        /** To the domain's unit specification. */
        Unit.Spec toSpec() {
            return new Unit.Spec(type, categoryId, reference, buildingId, outdoorAreaId, floor, annexOfUnitId,
                    saleable, rentable);
        }
    }

    /** A category without its scope (contract {@code UnitCategoryRef}). */
    record UnitCategoryRef(UUID id, String code, LocalizedText name) {
        /** From the entity. */
        static UnitCategoryRef of(UnitCategory c) {
            return new UnitCategoryRef(c.id(), c.code(), LocalizedText.of(c.name()));
        }
    }

    /** A unit with its category (contract {@code Unit}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record UnitResponse(UUID id, UUID siteId, UnitType type, UnitCategoryRef category, String reference,
                        UUID buildingId, UUID outdoorAreaId, Integer floor, UUID annexOfUnitId, boolean saleable,
                        boolean rentable, boolean active, long version) {
        /** From a unit and its category. */
        static UnitResponse of(UnitView view) {
            Unit u = view.unit();
            return new UnitResponse(u.id().value(), u.siteId().value(), u.type(), UnitCategoryRef.of(view.category()),
                    u.reference(), u.buildingId().map(id -> id.value()).orElse(null), u.outdoorAreaId().orElse(null),
                    u.floor().orElse(null), u.annexOfUnitId().map(id -> id.value()).orElse(null), u.saleable(),
                    u.rentable(), u.active(), u.version());
        }
    }

    // -------------------------------------------------------------------------------------- unit categories

    /** A category with its scope (contract {@code UnitCategory}, A6). */
    record UnitCategoryResponse(UUID id, String code, LocalizedText name, ScopeBody scope, UnitType unitType,
                                int sortOrder, boolean active, long version) {
        /** From the entity; the scope identifier is the site's, else the organization's, absent for the platform. */
        static UnitCategoryResponse of(UnitCategory c) {
            UUID scopeId = c.siteId().map(id -> id.value())
                    .orElseGet(() -> c.organizationId().map(id -> id.value()).orElse(null));
            return new UnitCategoryResponse(c.id(), c.code(), LocalizedText.of(c.name()),
                    new ScopeBody(c.scopeType(), scopeId), c.unitType(), c.sortOrder(), c.active(), c.version());
        }
    }

    /** Body of {@code POST /v1/unit-categories} (A6). */
    record UnitCategoryInput(
            @NotNull @Valid ScopeBody scope,
            @NotNull UnitType unitType,
            @NotNull @Pattern(regexp = "^[A-Z0-9][A-Z0-9_-]{0,29}$") String code,
            @NotNull @Valid LocalizedTextInput name,
            @Min(0) @Max(1000) Integer sortOrder) {
    }

    /** Body of {@code PUT /v1/unit-categories/{id}}. */
    record UpdateUnitCategoryRequest(
            @NotNull @Valid LocalizedTextInput name,
            @NotNull @Min(0) @Max(1000) Integer sortOrder,
            @NotNull Boolean active,
            @NotNull @PositiveOrZero Long version) {
    }

    // ------------------------------------------------------------------------------- back-office (ADR-035)

    /** An organization named in a response. */
    record OrganizationRef(UUID id, String name) {
    }

    /** A zone for the back-office (contract {@code AdminZone}): with its address count, creator and merge target. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AdminZoneResponse(UUID id, int level, String code, LocalizedText name, String countryCode, UUID parentId,
                             ZoneStatus status, List<ZoneRef> ancestors, long addressCount,
                             OrganizationRef createdByOrganization, ZoneRef mergedInto, Instant createdAt,
                             long version) {
        /** From the back-office view of a zone. */
        static AdminZoneResponse of(ZoneAdministration.AdminZoneView view) {
            Zone z = view.zone().zone();
            return new AdminZoneResponse(z.id(), z.level(), z.code(), LocalizedText.of(z.name()), z.countryCode(),
                    z.parentId().orElse(null), z.status(), view.zone().ancestors().stream().map(ZoneRef::of).toList(),
                    view.addressCount(),
                    view.createdBy().map(o -> new OrganizationRef(o.id().value(), o.name())).orElse(null),
                    view.mergedInto().map(ZoneRef::of).orElse(null), z.createdAt(), z.version());
        }
    }

    /** Body of {@code POST /v1/admin/zones} (A8, M7). */
    record ZoneInput(
            @NotNull @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
            UUID parentId,
            @NotNull @Pattern(regexp = "^[A-Za-z0-9_-]{1,20}$") String code,
            @NotNull @Valid LocalizedTextInput name) {
    }

    /** Body of {@code PUT /v1/admin/zones/{id}}. */
    record UpdateZoneRequest(
            @NotNull @Pattern(regexp = "^[A-Za-z0-9_-]{1,20}$") String code,
            @NotNull @Valid LocalizedTextInput name,
            @NotNull @PositiveOrZero Long version) {
    }

    /** Body of {@code POST /v1/admin/zones/{id}/validate}. */
    record ValidateZoneRequest(
            @NotNull @Pattern(regexp = "^[A-Za-z0-9_-]{1,20}$") String code,
            UUID parentId,
            @Valid LocalizedTextInput name,
            @NotNull @PositiveOrZero Long version) {
    }

    /** Body of {@code POST /v1/admin/zones/{id}/merge}. */
    record MergeZoneRequest(@NotNull UUID targetZoneId, @NotNull @PositiveOrZero Long version) {
    }

    /** Body of a deactivation with the version read. */
    record VersionedReasonRequest(@Size(max = 500) String reason, @NotNull @PositiveOrZero Long version) {
    }

    /** Body of {@code POST /v1/admin/unit-categories} (A6, M7). */
    record PlatformUnitCategoryInput(
            @NotNull UnitType unitType,
            @NotNull @Pattern(regexp = "^[A-Z0-9][A-Z0-9_-]{0,29}$") String code,
            @NotNull @Valid LocalizedTextInput name,
            @Min(0) @Max(1000) Integer sortOrder) {
    }

    /** One error of the unit import, by Excel row and column (contract {@code UnitImportError}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record UnitImportError(int row, String column, String code, String value) {
    }

    /** What the unit import did (contract {@code UnitImportReport}, ADR-027). */
    record UnitImportReport(String outcome, int totalRows, int createdCount, List<UnitImportError> errors) {
    }
}
