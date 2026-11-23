package com.almaraama.membership.adapters.in.startup;

import com.almaraama.membership.application.PlatformBootstrap;
import com.almaraama.shared.logging.LogMasking;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.stereotype.Component;

/**
 * Our team's accounts (ADR-026): at start-up, every person listed in the secret file {@code
 * ALMARAAMA_PLATFORM_ADMIN_EMAILS_FILE} becomes platform admin, idempotently and audited, never through the API. One
 * person per line, {@code email;phone} (phone in international form, mandatory for every account, D1); lines starting
 * with {@code #} are comments. A new account receives its "set my password" e-mail (ADR-031); nothing secret is
 * logged.
 */
@Component
class PlatformAdmins implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(PlatformAdmins.class);

    private final PlatformBootstrap bootstrap;
    private final String file;

    /**
     * @param bootstrap gives the platform admin role
     * @param file      path of the secret file listing our team; empty means none
     */
    PlatformAdmins(PlatformBootstrap bootstrap, @Value("${almaraama.platform-admin-emails-file:}") String file) {
        this.bootstrap = bootstrap;
        this.file = file;
    }

    /** Reads the file and makes each listed person a platform admin; a malformed line is skipped and logged. */
    @Override
    public void run(ApplicationArguments args) throws IOException {
        if (file == null || file.isBlank()) {
            return;
        }
        List<String> lines = Files.readAllLines(Path.of(file)).stream().map(String::strip)
                .filter(line -> !line.isEmpty() && !line.startsWith("#")).toList();
        for (String line : lines) {
            String[] parts = line.split(";");
            if (parts.length != 2) {
                LOG.warn("Platform admin line ignored: expected email;phone");
                continue;
            }
            bootstrap.ensurePlatformAdmin(parts[0].strip(), parts[1].strip(), "Admin", "almaraama");
            LOG.info("Platform admin ensured for {}", LogMasking.email(parts[0].strip()));
        }
    }
}
