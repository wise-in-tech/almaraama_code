package com.almaraama.membership.adapters.in.web;

import com.almaraama.identity.api.AccountStatus;
import com.almaraama.membership.domain.AdjustmentEffect;
import com.almaraama.membership.domain.KeyOutcome;
import com.almaraama.membership.domain.MembershipOrigin;
import com.almaraama.membership.domain.MembershipStatus;
import com.almaraama.membership.domain.OccupancyRole;
import com.almaraama.membership.domain.PreRegistrationOrigin;
import com.almaraama.membership.domain.PreRegistrationStatus;
import com.almaraama.membership.domain.RoleKind;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import com.almaraama.shared.i18n.Language;
import com.almaraama.shared.paging.PageResult;
import com.almaraama.sites.api.OrganizationType;
import com.almaraama.sites.api.UnitType;
import com.fasterxml.jackson.annotation.JsonInclude;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.PositiveOrZero;
import jakarta.validation.constraints.Size;
import java.time.Instant;
import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/** Request and response bodies of the membership endpoints, exactly as in api/openapi.yaml. Validated on entry. */
final class MembershipWebDtos {

    /** Holder of the request and response records only. */
    private MembershipWebDtos() {
    }

    // ------------------------------------------------------------------------------------------------ shared

    /** A text in the three interface languages (M1). */
    record LocalizedText(String ar, String en, String fr) {
    }

    /** Position of a page in the whole result. */
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

    /** A person named in a response. */
    record PersonRef(UUID id, String firstName, String lastName) {
    }

    /** Who to invite or onboard (D1): the e-mail is the sign-in identifier, the phone of any country is mandatory. */
    record PersonInput(
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 30) String phone,
            @NotNull Language language) {

        /** Never prints the contact details (ADR-033). */
        @Override
        public String toString() {
            return "PersonInput[details hidden]";
        }
    }

    /** A role code as sent by the client: the shape of {@code RoleCode} (roles are data, ADR-026). */
    static final String ROLE_CODE = "^[A-Z][A-Z0-9_]{1,49}$";

    /** Optional reason of a decision, kept in the history. */
    record ReasonRequest(@Size(max = 500) String reason) {
    }

    /** Where a role or permission applies; no identifier for the platform. */
    record Scope(@NotNull ScopeType type, UUID id) {
    }

    /** Contract codes ({@code unit:import}) to the catalogue; an unknown code is {@code PERMISSION_UNKNOWN}. */
    static Permission permission(String code) {
        return Permission.fromCode(code).orElseThrow(() -> new BusinessException("PERMISSION_UNKNOWN",
                ErrorCategory.RULE_VIOLATION, "Unknown permission"));
    }

    /** Contract codes to permissions; {@code PERMISSION_UNKNOWN} for any unknown code. */
    static Set<Permission> permissions(Collection<String> codes) {
        return codes.stream().map(MembershipWebDtos::permission).collect(Collectors.toSet());
    }

    /** Permissions to their contract codes, sorted. */
    static List<String> codes(Collection<Permission> permissions) {
        return permissions.stream().map(Permission::code).sorted().toList();
    }

    // ------------------------------------------------------------------------------------ catalogue, roles

    /** A permission of the catalogue, with whether it can be adjusted (C9). */
    record PermissionResponse(String code, List<ScopeType> scopeTypes, boolean adjustable, boolean sensitive,
                              LocalizedText label, LocalizedText description) {
    }

    /** A role, a row of the role table, with its default permissions (contract {@code RoleDefinition}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RoleDefinitionResponse(String code, RoleKind kind, List<ScopeType> scopeTypes, UUID organizationId,
                                  LocalizedText label, boolean grantsAll, boolean editable,
                                  List<String> defaultPermissions) {
    }

    /** A difference of the organization from a role's defaults (contract {@code RoleDelta}, C11). */
    record RoleDeltaResponse(UUID id, String permission, AdjustmentEffect effect, PersonRef createdBy,
                             Instant createdAt) {
    }

    /** A role's permissions in an organization: defaults, differences, result (contract {@code OrganizationRole}). */
    record OrganizationRoleResponse(String roleCode, List<String> defaultPermissions, List<RoleDeltaResponse> deltas,
                                    List<String> effectivePermissions, boolean customized, boolean editable) {
    }

    /** Body of {@code PUT /v1/organizations/{id}/roles/{roleCode}}: the permissions wanted; the server keeps the differences. */
    record SetRolePermissionsRequest(
            @NotNull @Size(max = 200) List<@NotBlank String> permissions,
            @Size(max = 500) String reason) {
    }

    /** A role and its scope, in invitations and people lists. */
    record RoleAssignmentSpec(@NotNull @Pattern(regexp = ROLE_CODE) String roleCode, @NotNull @Valid Scope scope) {
    }

    /** Body of {@code POST /v1/organizations/{id}/role-assignments}. */
    record RoleAssignmentRequest(@NotNull UUID personId, @NotNull @Pattern(regexp = ROLE_CODE) String roleCode,
                                 @NotNull @Valid Scope scope) {
    }

    /** A role assignment with who gave and ended it. */
    record RoleAssignmentResponse(UUID id, PersonRef person, String roleCode, Scope scope, String scopeName,
                                  PersonRef assignedBy, Instant assignedAt, PersonRef endedBy, Instant endedAt,
                                  String endReason) {
    }

    /** Body of {@code POST /v1/organizations/{id}/permission-adjustments}. */
    record PermissionAdjustmentRequest(
            @NotNull UUID personId,
            @NotNull @Valid Scope scope,
            @NotBlank String permission,
            @NotNull AdjustmentEffect effect,
            @Size(max = 500) String reason) {
    }

    /** A person-level adjustment with who made and cancelled it. */
    record PermissionAdjustmentResponse(UUID id, PersonRef person, Scope scope, String permission,
                                        AdjustmentEffect effect, String reason, PersonRef createdBy,
                                        Instant createdAt, PersonRef cancelledBy, Instant cancelledAt) {
    }

    /** A permission a person has on a scope and where it comes from (contract {@code PersonPermissions}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record EffectivePermission(String permission, String origin, String roleCode, UUID adjustmentId) {
    }

    /** A role permission the person does not have, and why. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record RemovedPermission(String permission, String origin, UUID adjustmentId) {
    }

    /** A person's roles and permissions on one scope. */
    record ScopePermissionsResponse(Scope scope, String scopeName, List<String> roles,
                                    List<EffectivePermission> permissions, List<RemovedPermission> removed) {
    }

    /** A person's permissions in an organization, scope by scope. */
    record PersonPermissionsResponse(UUID personId, List<ScopePermissionsResponse> scopes) {
    }

    // --------------------------------------------------------------------------------- people, invitations

    /** Body of {@code POST /v1/organizations}: the client in its country and its first promoteur (C8, M4). */
    record CreateOrganizationRequest(
            @NotNull OrganizationType type,
            @NotBlank @Size(max = 200) String name,
            @NotNull @Pattern(regexp = "^[A-Z]{2}$") String countryCode,
            @Size(max = 30) String contactPhone,
            @Email @Size(max = 254) String contactEmail,
            @NotNull @Valid PersonInput owner) {
    }

    /** An organization just created (contract {@code Organization}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record OrganizationResponse(UUID id, OrganizationType type, String name, String countryCode, String currencyCode,
                                String contactPhone, String contactEmail, boolean active, long version) {
    }

    /** Result of an invitation; the link itself went to the person's e-mail (ADR-031). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record InvitationResult(UUID personId, String outcome, Instant linkExpiresAt) {
    }

    /** A password link was requested for a person (contract {@code PasswordLinkSent}). */
    record PasswordLinkSent(String kind, Instant expiresAt) {
    }

    /** The new organization and its owner's invitation. */
    record OrganizationCreated(OrganizationResponse organization, InvitationResult owner) {
    }

    /** A person of the organization with their roles. */
    record OrganizationPersonResponse(UUID id, String firstName, String lastName, String email, String phone,
                                      AccountStatus accountStatus, List<RoleAssignmentSpec> roles,
                                      boolean hasAdjustments, List<UUID> siteIds) {
    }

    /** Body of {@code POST /v1/organizations/{id}/invitations}: the person and their roles. */
    record OrganizationInvitationRequest(
            @NotNull @Valid PersonInput person,
            @NotNull @Size(min = 1, max = 50) List<@Valid RoleAssignmentSpec> roles) {
    }

    /** A unit the person occupies, as owner or tenant (E1). */
    record OccupancyInput(
            @NotNull UUID unitId,
            @NotNull OccupancyRole role,
            Boolean primaryTenant,
            LocalDate startedOn) {
    }

    /** Body of {@code POST /v1/sites/{id}/invitations}: the person, their units, their buildings and their role. */
    record SiteInvitationRequest(
            @NotNull @Valid PersonInput person,
            @Pattern(regexp = ROLE_CODE) String roleCode,
            @Size(max = 50) List<@Valid OccupancyInput> occupancies,
            @Size(max = 200) List<UUID> allowedBuildingIds) {
    }

    // ------------------------------------------------------------------------------------------ memberships

    /** A person's access to a site, with its decision. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SiteMembershipResponse(UUID id, UUID siteId, PersonRef person, String phone, MembershipStatus status,
                                  MembershipOrigin origin, List<UUID> allowedBuildingIds, String comment,
                                  KeyOutcome keyOutcome, UUID preRegistrationId, Instant submittedAt,
                                  PersonRef decidedBy, Instant decidedAt, String decisionReason, Instant createdAt,
                                  Instant endedAt, long version) {
    }

    /** Body of the approval: the buildings opened, the confirmed units and the version read. */
    record ApproveMembershipRequest(
            @NotNull @Size(max = 200) List<UUID> allowedBuildingIds,
            @NotNull @Size(max = 50) List<@Valid OccupancyInput> occupancies,
            @NotNull @PositiveOrZero Long version) {
    }

    /** Body of the building restriction (C7), with the version read. */
    record AllowedBuildingsRequest(
            @NotNull @Size(max = 200) List<UUID> buildingIds,
            @NotNull @PositiveOrZero Long version) {
    }

    /** A site's registration QR code (D2, ADR-034): the code in groups and the address the QR code holds. */
    record RegistrationQrResponse(UUID siteId, String code, String registrationUrl, boolean enabled,
                                  Instant updatedAt, long version) {
    }

    /** Activates or deactivates registration on the site, with the version read. */
    record RegistrationQrSettings(@NotNull Boolean enabled, @NotNull @PositiveOrZero Long version) {
    }

    /** A registration code as typed or scanned. */
    record RegistrationCodeRequest(@NotBlank @Size(min = 8, max = 20) String code) {
    }

    /** What a person sees before registering: names only, the list of sites is never public (D2). */
    record RegistrationQrPreview(String siteName, String organizationName) {
    }

    /** Body of the public registration form (D2, D3). Never logged. */
    record RegistrationRequest(
            @NotBlank @Size(min = 8, max = 20) String code,
            @NotBlank @Size(max = 100) String firstName,
            @NotBlank @Size(max = 100) String lastName,
            @NotBlank @Email @Size(max = 254) String email,
            @NotBlank @Size(max = 30) String phone,
            @NotNull Language language,
            @Size(max = 500) String comment,
            @NotNull @Size(min = 1, max = 1000) String password,
            @Size(max = 20) String preRegistrationKey) {

        /** Never prints the password, the key, the contact details nor the comment (ADR-033). */
        @Override
        public String toString() {
            return "RegistrationRequest[details hidden]";
        }
    }

    /** The single answer of the public form, whatever happened (ADR-034). */
    record RegistrationAccepted(String status) {
    }

    /** Body of {@code POST /v1/me/access-requests}: one more site for a signed-in person (D2 step 5, D6). */
    record SiteAccessRequest(
            @NotBlank @Size(min = 8, max = 20) String code,
            @Size(max = 500) String comment,
            @Size(max = 20) String preRegistrationKey) {

        /** Never prints the key nor the comment. */
        @Override
        public String toString() {
            return "SiteAccessRequest[details hidden]";
        }
    }

    // ---------------------------------------------------------------------------------- pre-registrations

    /** A unit planned for a pre-registered person (D3). */
    record PlannedOccupancyInput(@NotNull UUID unitId, @NotNull OccupancyRole role, Boolean primaryTenant) {
    }

    /** A planned unit with its reference and type, in responses. */
    record PlannedOccupancyResponse(UUID unitId, OccupancyRole role, boolean primaryTenant, String unitReference,
                                    UnitType unitType) {
    }

    /** Body of {@code POST /v1/sites/{id}/pre-registrations}. */
    record PreRegistrationRequest(
            @NotNull @Valid PersonInput person,
            @NotNull @Size(min = 1, max = 50) List<@Valid PlannedOccupancyInput> plannedOccupancies,
            @Min(1) @Max(90) Integer keyValidDays) {
    }

    /** Body of the key renewal, with the version read. */
    record RenewPreRegistrationKeyRequest(@Min(1) @Max(90) Integer keyValidDays, @NotNull @PositiveOrZero Long version) {
    }

    /** A pre-registration, never its key. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record PreRegistrationResponse(UUID id, UUID siteId, String firstName, String lastName, String email, String phone,
                                   Language language, List<PlannedOccupancyResponse> plannedOccupancies,
                                   PreRegistrationStatus status, PreRegistrationOrigin origin, Instant expiresAt,
                                   PersonRef createdBy, Instant createdAt, Instant usedAt, UUID membershipId,
                                   Instant cancelledAt, String cancelReason, long version) {
    }

    /** A pre-registration with its key, returned this once (D3). */
    record PreRegistrationIssued(PreRegistrationResponse preRegistration, String key) {

        /** Never prints the key. */
        @Override
        public String toString() {
            return "PreRegistrationIssued[" + preRegistration.id() + "]";
        }
    }

    /** One error of the resident import, by Excel row and column. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record ResidentImportError(int row, String column, String code, String value) {
    }

    /** A key of the resident import, shown this once. */
    record ResidentImportKey(UUID preRegistrationId, String firstName, String lastName, String email, String key,
                             Instant expiresAt) {
    }

    /** What the resident import did (ADR-027). */
    record ResidentImportReport(String outcome, int totalRows, int createdCount, List<ResidentImportError> errors,
                                List<ResidentImportKey> keys) {
    }

    // ------------------------------------------------------------------------- organizations (ADR-035)

    /** An organization for the back-office (contract {@code AdminOrganization}). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record AdminOrganizationResponse(UUID id, OrganizationType type, String name, String countryCode,
                                     String currencyCode, String contactPhone, String contactEmail, boolean active,
                                     long version, long siteCount, long ownerCount, Instant createdAt,
                                     Instant deactivatedAt) {
    }

    /** A mandatory reason of a decision of our team. */
    record RequiredReasonRequest(@NotBlank @Size(min = 3, max = 500) String reason) {
    }

    // ------------------------------------------------------------------------------------ support (ADR-035)

    /** An organization named in a response. */
    record OrganizationRef(UUID id, String name) {
    }

    /** Body of the opening of a support session: the organization and why (C12). */
    record StartSupportSessionRequest(@NotNull UUID organizationId, @NotBlank @Size(min = 10, max = 500) String reason) {
    }

    /** A support session. */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record SupportSessionResponse(UUID id, OrganizationRef organization, PersonRef administrator, String reason,
                                  String status, Instant startedAt, Instant expiresAt, Instant endedAt) {
    }

    // ------------------------------------------------------------------------------------------ occupancies

    /** Body of {@code POST /v1/units/{id}/occupancies}. */
    record StartOccupancyRequest(
            @NotNull UUID personId,
            @NotNull UUID unitId,
            @NotNull OccupancyRole role,
            Boolean primaryTenant,
            LocalDate startedOn) {
    }

    /** Body of {@code POST /v1/occupancies/{id}/end} (E2). */
    record EndOccupancyRequest(@NotNull LocalDate endedOn, @NotBlank @Size(max = 500) String reason) {
    }

    /** An occupancy with its occupant. */
    record OccupancyResponse(UUID id, UUID unitId, UUID siteId, PersonRef person, OccupancyRole role,
                             boolean primaryTenant, LocalDate startedOn, LocalDate endedOn, String endReason) {
    }

    // -------------------------------------------------------------------------------------------------- me

    /** An organization where the caller holds a role. */
    record MyOrganization(UUID id, OrganizationType type, String name, List<String> roles, List<String> permissions) {
    }

    /** A site of the caller's perimeter with their effective permissions. */
    record MySite(UUID id, UUID organizationId, String name, List<String> roles, List<String> permissions,
                  List<UUID> allowedBuildingIds, List<UUID> occupiedUnitIds) {
    }

    /** What the caller can see and do, to build the menus, with their open support session (ADR-035). */
    @JsonInclude(JsonInclude.Include.NON_NULL)
    record MyAccess(SupportSessionResponse supportSession, List<String> platformPermissions,
                    List<MyOrganization> organizations, List<MySite> sites) {
    }

    /** A unit the caller occupies. */
    record MyOccupancy(UUID occupancyId, UUID unitId, String unitReference, UnitType unitType, OccupancyRole role,
                       LocalDate startedOn) {
    }

    /** One of the caller's site accesses with their units. */
    record MyMembership(UUID membershipId, UUID siteId, String siteName, String organizationName,
                        MembershipStatus status, List<MyOccupancy> occupancies) {
    }
}
