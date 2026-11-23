package com.almaraama.audit.adapters.in.events;

import com.almaraama.membership.api.events.RegistrationQrRotatedV1;
import com.almaraama.membership.api.events.RegistrationQrEnabledChangedV1;
import com.almaraama.membership.api.events.PreRegistrationCreatedV1;
import com.almaraama.membership.api.events.PreRegistrationKeyRenewedV1;
import com.almaraama.membership.api.events.PreRegistrationCancelledV1;
import com.almaraama.membership.api.events.PreRegistrationUsedV1;
import com.almaraama.membership.api.events.MembershipAutoApprovedV1;
import com.almaraama.membership.api.events.SupportSessionStartedV1;
import com.almaraama.membership.api.events.SupportSessionEndedV1;
import com.almaraama.audit.domain.AuditEntry;
import com.almaraama.audit.domain.AuditJournal;
import com.almaraama.membership.api.events.MembershipApprovedV1;
import com.almaraama.membership.api.events.MembershipDeactivatedV1;
import com.almaraama.membership.api.events.MembershipRejectedV1;
import com.almaraama.membership.api.events.OrganizationRolePermissionsChangedV1;
import com.almaraama.membership.api.events.PermissionAdjustedV1;
import com.almaraama.membership.api.events.PermissionAdjustmentCancelledV1;
import com.almaraama.membership.api.events.RoleAssignedV1;
import com.almaraama.membership.api.events.RoleAssignmentEndedV1;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.actor.Actor;
import com.almaraama.shared.id.OrganizationId;
import com.almaraama.shared.id.SiteId;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.modulith.events.ApplicationModuleListener;
import org.springframework.stereotype.Component;

/**
 * Records every change of roles and permissions (RB-5, security baseline: permission changes) and the decisions on
 * site accesses. Runs after the membership transaction committed; retried from the outbox on failure (CF-6).
 */
@Component
class MembershipEventsAuditor {

    private static final String PERSON = "PERSON";

    private final AuditJournal journal;

    /** @param journal the append-only journal */
    MembershipEventsAuditor(AuditJournal journal) {
        this.journal = journal;
    }

    /** Journals {@code ROLE_ASSIGNED} with the role and its scope (RB-5). */
    @ApplicationModuleListener
    void on(RoleAssignedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "ROLE_ASSIGNED", PERSON,
                event.personId().value(), scope(event.organizationId(), event.siteId(), Map.of(
                        "assignmentId", event.assignmentId().toString(), "role", event.role().value(),
                        "scopeType", event.scopeType().name()))));
    }

    /** Journals {@code ROLE_ENDED} with the role and its scope (RB-5). */
    @ApplicationModuleListener
    void on(RoleAssignmentEndedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "ROLE_ENDED", PERSON,
                event.personId().value(), scope(event.organizationId(), event.siteId(), Map.of(
                        "assignmentId", event.assignmentId().toString(), "role", event.role().value(),
                        "scopeType", event.scopeType().name()))));
    }

    /** Journals {@code ROLE_PERMISSIONS_CHANGED} with the permissions before and after (RB-5). */
    @ApplicationModuleListener
    void on(OrganizationRolePermissionsChangedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "ROLE_PERMISSIONS_CHANGED", "ORGANIZATION",
                event.organizationId().value(), Map.of("role", event.role().value(),
                        "before", codes(event.before()), "after", codes(event.after()))));
    }

    /** Journals {@code PERMISSION_ADJUSTED}: the permission, its effect and scope (RB-5). */
    @ApplicationModuleListener
    void on(PermissionAdjustedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "PERMISSION_ADJUSTED", PERSON,
                event.personId().value(), scope(Optional.of(event.organizationId()), event.siteId(), Map.of(
                        "adjustmentId", event.adjustmentId().toString(), "permission", event.permission().code(),
                        "effect", event.effect()))));
    }

    /** Journals {@code PERMISSION_ADJUSTMENT_CANCELLED} (RB-5). */
    @ApplicationModuleListener
    void on(PermissionAdjustmentCancelledV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "PERMISSION_ADJUSTMENT_CANCELLED", PERSON,
                event.personId().value(), Map.of("adjustmentId", event.adjustmentId().toString(),
                        "organizationId", event.organizationId().toString(),
                        "permission", event.permission().code(), "effect", event.effect())));
    }

    /** Journals {@code ACCESS_APPROVED} (D3). */
    @ApplicationModuleListener
    void on(MembershipApprovedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "ACCESS_APPROVED", PERSON,
                event.personId().value(), Map.of("membershipId", event.membershipId().toString(),
                        "organizationId", event.organizationId().toString(), "siteId", event.siteId().toString())));
    }

    /** Journals {@code ACCESS_REJECTED} (D3). */
    @ApplicationModuleListener
    void on(MembershipRejectedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "ACCESS_REJECTED", PERSON,
                event.personId().value(), Map.of("membershipId", event.membershipId().toString(),
                        "organizationId", event.organizationId().toString(), "siteId", event.siteId().toString())));
    }

    /** Journals {@code ACCESS_DEACTIVATED} (D6). */
    @ApplicationModuleListener
    void on(MembershipDeactivatedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "ACCESS_DEACTIVATED", PERSON,
                event.personId().value(), Map.of("membershipId", event.membershipId().toString(),
                        "organizationId", event.organizationId().toString(), "siteId", event.siteId().toString())));
    }

    /** Journals {@code REGISTRATION_QR_ROTATED}: the old code stopped working (D2, criterion 4). */
    @ApplicationModuleListener
    void on(RegistrationQrRotatedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "REGISTRATION_QR_ROTATED", "SITE",
                event.siteId().value(), Map.of("organizationId", event.organizationId().toString())));
    }

    /** Journals {@code REGISTRATION_QR_ENABLED} or {@code REGISTRATION_QR_DISABLED} (D2, criterion 4). */
    @ApplicationModuleListener
    void on(RegistrationQrEnabledChangedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(),
                event.enabled() ? "REGISTRATION_QR_ENABLED" : "REGISTRATION_QR_DISABLED", "SITE",
                event.siteId().value(), Map.of("organizationId", event.organizationId().toString())));
    }

    /** Journals {@code PRE_REGISTRATION_CREATED} (D3); never the key nor the person's details. */
    @ApplicationModuleListener
    void on(PreRegistrationCreatedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "PRE_REGISTRATION_CREATED",
                "PRE_REGISTRATION", event.preRegistrationId(), Map.of("siteId", event.siteId().toString(),
                        "origin", event.origin(), "plannedUnits", String.valueOf(event.plannedUnits()))));
    }

    /** Journals {@code PRE_REGISTRATION_KEY_RENEWED} (D3). */
    @ApplicationModuleListener
    void on(PreRegistrationKeyRenewedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "PRE_REGISTRATION_KEY_RENEWED",
                "PRE_REGISTRATION", event.preRegistrationId(), Map.of("siteId", event.siteId().toString())));
    }

    /** Journals {@code PRE_REGISTRATION_CANCELLED} (D3, M2). */
    @ApplicationModuleListener
    void on(PreRegistrationCancelledV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), event.actor(), "PRE_REGISTRATION_CANCELLED",
                "PRE_REGISTRATION", event.preRegistrationId(), Map.of("siteId", event.siteId().toString())));
    }

    /** Journals {@code PRE_REGISTRATION_USED} by the person, and whether it approved the access (D3). */
    @ApplicationModuleListener
    void on(PreRegistrationUsedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), Actor.person(event.personId()), "PRE_REGISTRATION_USED",
                "PRE_REGISTRATION", event.preRegistrationId(), Map.of("siteId", event.siteId().toString(),
                        "membershipId", event.membershipId().toString(),
                        "autoApproved", String.valueOf(event.autoApproved()))));
    }

    /** Journals {@code ACCESS_AUTO_APPROVED}: an access approved by a pre-registration key (D3). */
    @ApplicationModuleListener
    void on(MembershipAutoApprovedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(), Actor.person(event.personId()), "ACCESS_AUTO_APPROVED",
                PERSON, event.personId().value(), Map.of("siteId", event.siteId().toString(),
                        "membershipId", event.membershipId().toString())));
    }

    /** Journals {@code SUPPORT_SESSION_STARTED} with its reason and end (C12, ADR-035). */
    @ApplicationModuleListener
    void on(SupportSessionStartedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(),
                Actor.person(event.administratorId()).inSupportSession(event.sessionId()), "SUPPORT_SESSION_STARTED",
                "ORGANIZATION", event.organizationId().value(), Map.of("reason", event.reason(),
                        "expiresAt", event.expiresAt().toString())));
    }

    /** Journals {@code SUPPORT_SESSION_ENDED} (C12, ADR-035). */
    @ApplicationModuleListener
    void on(SupportSessionEndedV1 event) {
        journal.append(AuditEntry.of(event.occurredAt(),
                Actor.person(event.administratorId()).inSupportSession(event.sessionId()), "SUPPORT_SESSION_ENDED",
                "ORGANIZATION", event.organizationId().value(), Map.of()));
    }

    /** The details with the organization and site added when present. */
    private static Map<String, String> scope(Optional<OrganizationId> organization, Optional<SiteId> site,
                                             Map<String, String> details) {
        Map<String, String> all = new HashMap<>(details);
        organization.ifPresent(id -> all.put("organizationId", id.toString()));
        site.ifPresent(id -> all.put("siteId", id.toString()));
        return all;
    }

    /** Permission codes, sorted and comma-separated. */
    private static String codes(Set<Permission> permissions) {
        return permissions.stream().map(Permission::code).sorted().collect(Collectors.joining(","));
    }
}
