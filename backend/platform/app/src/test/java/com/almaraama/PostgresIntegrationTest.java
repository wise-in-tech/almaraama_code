package com.almaraama;

import java.nio.file.Files;
import java.nio.file.Path;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.testcontainers.postgresql.PostgreSQLContainer;
import org.testcontainers.utility.MountableFile;

/**
 * Real PostgreSQL 17 set up with the same role script as Docker Compose: Flyway runs as the migration role and the
 * application as the restricted application role, so every test also exercises the database rights (M2).
 * One container for all test classes, stopped when the JVM exits. E-mails are recorded, never sent (ADR-031).
 */
@Import(RecordingEmailSender.Configuration.class)
abstract class PostgresIntegrationTest {

    static final String APP_ROLE = "almaraama_app";

    static final PostgreSQLContainer POSTGRES = new PostgreSQLContainer("postgres:17-alpine")
            .withDatabaseName("almaraama")
            .withEnv("ALMARAAMA_DB_MIGRATOR_USER", "almaraama_migrator")
            .withEnv("ALMARAAMA_DB_MIGRATOR_PASSWORD", "migrator-test-password")
            .withEnv("ALMARAAMA_DB_APP_USER", APP_ROLE)
            .withEnv("ALMARAAMA_DB_APP_PASSWORD", "app-test-password")
            .withCopyFileToContainer(
                    MountableFile.forHostPath(repositoryFile("docker/postgres/init/01-roles.sh"), 0755),
                    "/docker-entrypoint-initdb.d/01-roles.sh");

    static {
        POSTGRES.start();
    }

    @DynamicPropertySource
    static void database(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", POSTGRES::getJdbcUrl);
        registry.add("spring.datasource.username", () -> APP_ROLE);
        registry.add("spring.datasource.password", () -> "app-test-password");
        registry.add("spring.flyway.user", () -> "almaraama_migrator");
        registry.add("spring.flyway.password", () -> "migrator-test-password");
        registry.add("almaraama.identity.jwt.allow-ephemeral-key", () -> "true");
        registry.add("spring.flyway.locations", () -> "classpath:db/migration,classpath:db/dev");
        // ADR-032: throwaway key and an easy challenge, so a test solves it in a few milliseconds.
        registry.add("almaraama.altcha.allow-ephemeral-key", () -> "true");
        registry.add("almaraama.altcha.max-number", () -> "2000");
        // ADR-038: throwaway link key and a temporary directory for the stored files.
        registry.add("almaraama.files.allow-ephemeral-key", () -> "true");
        registry.add("almaraama.files.directory", () -> System.getProperty("java.io.tmpdir") + "/almaraama-test-files");
        // No background job during a test (outbox resubmission).
        registry.add("almaraama.scheduling.enabled", () -> "false");
    }

    /** Finds a file from the repository root, whichever Maven module the tests run from. */
    static Path repositoryFile(String relativePath) {
        for (Path dir = Path.of("").toAbsolutePath(); dir != null; dir = dir.getParent()) {
            Path candidate = dir.resolve(relativePath);
            if (Files.isRegularFile(candidate)) {
                return candidate;
            }
        }
        throw new IllegalStateException("Not found from the working directory upwards: " + relativePath);
    }
}
