package com.almaraama.membership.adapters.out.persistence;

import com.almaraama.membership.domain.Role;
import com.almaraama.membership.domain.RoleKind;
import com.almaraama.shared.access.Permission;
import com.almaraama.shared.access.RoleCode;
import com.almaraama.shared.access.ScopeType;
import com.almaraama.shared.id.OrganizationId;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.stream.Collectors;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

/**
 * Reads the role tables ({@code membership.role}, {@code membership.role_default_permission}), which the application
 * never writes: they are filled by migration or by hand (ADR-026 revision 2). Plain SQL with no parameter.
 */
@Component
class JdbcRoleCatalog {

    private static final Logger LOG = LoggerFactory.getLogger(JdbcRoleCatalog.class);

    private final JdbcTemplate jdbc;

    /** @param jdbc runs the read-only queries */
    JdbcRoleCatalog(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    /** Every active role, by sort order; a row with an unknown kind or scope is skipped and logged. */
    List<Role> roles() {
        return jdbc.query("""
                        select code, kind, scope_types, grants_all, organization_id, name_ar, name_en, name_fr,
                               sort_order
                        from membership.role where active order by sort_order, code""",
                (rs, i) -> {
                    try {
                        Set<ScopeType> scopes = Arrays.stream(rs.getString("scope_types").split(","))
                                .map(String::strip).filter(s -> !s.isEmpty()).map(ScopeType::valueOf)
                                .collect(Collectors.toSet());
                        UUID organization = rs.getObject("organization_id", UUID.class);
                        return new Role(RoleCode.of(rs.getString("code")), RoleKind.valueOf(rs.getString("kind")),
                                scopes, rs.getBoolean("grants_all"),
                                Optional.ofNullable(organization).map(OrganizationId::new),
                                new Role.Name(rs.getString("name_ar"), rs.getString("name_en"),
                                        rs.getString("name_fr")), rs.getInt("sort_order"));
                    } catch (IllegalArgumentException e) {
                        LOG.warn("Role row {} ignored: {}", rs.getString("code"), e.getMessage());
                        return null;
                    }
                }).stream().filter(Objects::nonNull).toList();
    }

    /** Every role's default permissions; codes unknown to the catalogue are skipped and logged. */
    Map<RoleCode, Set<Permission>> defaults() {
        Map<RoleCode, Set<Permission>> defaults = new HashMap<>();
        jdbc.query("select role_code, permission from membership.role_default_permission", rs -> {
            String code = rs.getString("permission");
            Optional<Permission> permission = Permission.fromCode(code);
            if (permission.isEmpty()) {
                LOG.warn("Default permission {} of role {} unknown to the catalogue: ignored", code,
                        rs.getString("role_code"));
                return;
            }
            defaults.computeIfAbsent(RoleCode.of(rs.getString("role_code")), k -> EnumSet.noneOf(Permission.class))
                    .add(permission.get());
        });
        return defaults;
    }
}
