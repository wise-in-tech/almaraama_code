package com.almaraama.membership.application;

import com.almaraama.membership.domain.Role;
import com.almaraama.membership.domain.RoleKind;
import com.almaraama.membership.domain.RoleRepository;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.access.RoleCode;
import com.almaraama.shared.error.BusinessException;
import com.almaraama.shared.error.ErrorCategory;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;
import org.springframework.stereotype.Component;

/**
 * The roles and their default permissions, read from the role tables (ADR-026 revision 2) and kept in memory for one
 * minute: they change only by migration or by hand in the database, and a change applies within a minute without a
 * restart (criterion 17). The organizations' differences are never cached (RB-4).
 * <p>
 * Business rules never name a role. The three {@code SEED_*} codes below are the only exception allowed by ADR-026:
 * they name the rows the platform needs to bootstrap itself (our team, the first promoteur of a new organization) and
 * the role given by default with a site access (a resident). Each is used in exactly one place.
 */
@Component
public class RoleCatalog {

    /** Role of our team, given at start-up from the secret file of e-mails (ADR-026). */
    public static final RoleCode SEED_PLATFORM_ADMIN = RoleCode.of("PLATFORM_ADMIN");
    /** Role of the first person of a new organization (C8): the role with {@code grantsAll}. */
    public static final RoleCode SEED_ORGANIZATION_ADMIN = RoleCode.of("ORG_ADMIN");
    /** Role given with a site access when none is chosen (D2, D4). */
    public static final RoleCode SEED_DEFAULT_OCCUPANT = RoleCode.of("RESIDENT");

    private static final Duration REFRESH = Duration.ofMinutes(1);

    private final RoleRepository roles;
    private final Clock clock;
    private volatile Snapshot snapshot;

    /** The roles and defaults read at one time. */
    private record Snapshot(Map<RoleCode, Role> roles, Map<RoleCode, Set<Permission>> defaults, Instant readAt) {
    }

    /**
     * @param roles the role tables
     * @param clock time of the last read
     */
    RoleCatalog(RoleRepository roles, Clock clock) {
        this.roles = roles;
        this.clock = clock;
    }

    /** Every active role, by sort order. */
    public List<Role> all() {
        return List.copyOf(current().roles().values());
    }

    /** A role by code. */
    public Optional<Role> find(RoleCode code) {
        return Optional.ofNullable(current().roles().get(code));
    }

    /** A role by code; {@code ROLE_UNKNOWN} (422) when no row has this code. */
    public Role require(RoleCode code) {
        return find(code).orElseThrow(() -> new BusinessException("ROLE_UNKNOWN", ErrorCategory.RULE_VIOLATION,
                "Unknown role"));
    }

    /** The default permissions of a role (specification section 3.3); empty for a {@code grantsAll} role. */
    public Set<Permission> defaults(RoleCode code) {
        return current().defaults().getOrDefault(code, Set.of());
    }

    /** Whether the role grants every permission of its organization (C8). */
    public boolean grantsAll(RoleCode code) {
        return find(code).map(Role::grantsAll).orElse(false);
    }

    /** The codes of the roles that grant every permission (to keep the last one, RB-2). */
    public Set<RoleCode> grantsAllRoles() {
        return current().roles().values().stream().filter(Role::grantsAll).map(Role::code)
                .collect(Collectors.toUnmodifiableSet());
    }

    /** Whether the role is of this kind. */
    public boolean isKind(RoleCode code, RoleKind kind) {
        return find(code).map(r -> r.kind() == kind).orElse(false);
    }

    /** The cached roles, read again when older than one minute. */
    private Snapshot current() {
        Snapshot now = snapshot;
        Instant time = clock.instant();
        if (now == null || now.readAt().plus(REFRESH).isBefore(time)) {
            Map<RoleCode, Role> byCode = new LinkedHashMap<>();
            roles.roles().forEach(role -> byCode.put(role.code(), role));
            now = new Snapshot(byCode, roles.defaults(), time);
            snapshot = now;
        }
        return now;
    }

    /** Forgets the cached roles, so that the next read sees the database (tests of criterion 17). */
    public void refresh() {
        snapshot = null;
    }
}
