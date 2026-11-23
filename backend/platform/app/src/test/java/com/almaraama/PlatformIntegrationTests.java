package com.almaraama;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Starts the whole platform on a real PostgreSQL 17 and checks the platform-wide guarantees.
 */
@SpringBootTest(
        webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT,
        properties = {"management.server.port=0", "almaraama.security.auth-requests-per-minute=10000"})
class PlatformIntegrationTests extends PostgresIntegrationTest {

    @Autowired
    JdbcTemplate jdbc;

    @Value("${local.server.port}")
    int port;

    @Value("${local.management.port}")
    int managementPort;

    private final HttpClient http = HttpClient.newHttpClient();

    private static final List<String> SCHEMAS = List.of("platform", "identity", "sites", "membership", "assets",
            "maintenance", "preventive", "workflow", "customization", "notification", "audit", "files");

    @Test
    void CF3_eachModuleHasItsOwnSchema() {
        var schemas = jdbc.queryForList(
                "SELECT schema_name FROM information_schema.schemata", String.class);

        assertThat(schemas).containsAll(SCHEMAS);
    }

    @Test
    void M2_everyFutureTableDeniesDeleteToTheApplicationRole() {
        // Default privileges decide the rights on tables that modules will create later in their schemas.
        var tablePrivileges = jdbc.query("""
                SELECT n.nspname AS schema, string_agg(a.privilege_type, ',' ORDER BY a.privilege_type) AS privileges
                FROM pg_default_acl d
                JOIN pg_namespace n ON n.oid = d.defaclnamespace
                CROSS JOIN LATERAL aclexplode(d.defaclacl) a
                WHERE d.defaclobjtype = 'r' AND a.grantee = current_user::regrole
                GROUP BY n.nspname
                """, (rs, i) -> Map.entry(rs.getString("schema"), rs.getString("privileges")));

        assertThat(tablePrivileges).hasSize(SCHEMAS.size());
        assertThat(tablePrivileges).allSatisfy(entry -> assertThat(entry.getValue())
                .as("default table privileges in schema %s", entry.getKey())
                .isEqualTo("INSERT,SELECT,UPDATE"));
    }

    @Test
    void applicationRoleCannotChangeTheSchema() {
        for (String schema : SCHEMAS) {
            assertThat(jdbc.queryForObject("SELECT has_schema_privilege(current_user, ?, 'CREATE')",
                    Boolean.class, schema)).as("CREATE on schema %s", schema).isFalse();
        }
    }

    @Test
    void M2_applicationRoleCannotDelete() {
        String sql = "SELECT has_table_privilege(current_user, 'platform.event_publication', ?)";

        assertThat(jdbc.queryForObject("SELECT current_user", String.class)).isEqualTo(APP_ROLE);
        assertThat(jdbc.queryForObject(sql, Boolean.class, "INSERT")).isTrue();
        assertThat(jdbc.queryForObject(sql, Boolean.class, "DELETE")).isFalse();
        assertThat(jdbc.queryForObject(sql, Boolean.class, "TRUNCATE")).isFalse();
    }

    @Test
    void healthIsUpOnTheManagementPort() throws Exception {
        var response = get(managementPort, "/actuator/health");

        assertThat(response.statusCode()).isEqualTo(200);
        assertThat(response.body()).contains("\"UP\"");
    }

    @Test
    void businessEndpointsNeedAnAccessToken() throws Exception {
        var response = get(port, "/api/v1/anything");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(response.body()).contains("\"code\":\"UNAUTHENTICATED\"");
        assertThat(response.headers().firstValue("Content-Security-Policy")).hasValueSatisfying(
                csp -> assertThat(csp).contains("default-src 'none'"));
        assertThat(response.headers().firstValue("X-Frame-Options")).contains("DENY");
        assertThat(response.body()).doesNotContain("Exception", "at com.");
    }

    private HttpResponse<String> get(int targetPort, String path) throws Exception {
        var request = HttpRequest.newBuilder(URI.create("http://localhost:" + targetPort + path)).GET().build();
        return http.send(request, HttpResponse.BodyHandlers.ofString());
    }
}
