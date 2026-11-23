package com.almaraama.membership.adapters.in.startup;

import com.almaraama.identity.api.InvitationIssued;
import com.almaraama.membership.application.PlatformBootstrap;
import com.almaraama.shared.logging.LogMasking;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Component;

/**
 * Development only (profile {@code dev}): the person with {@code ALMARAAMA_DEV_INVITE_EMAIL} becomes platform admin
 * and promoteur ({@code ORG_ADMIN}) of an Algerian demo organization. While they have no password, their "set my
 * password" e-mail arrives in Mailpit (http://127.0.0.1:8025, ADR-031). Never active in staging or production.
 */
@Component
@Profile("dev")
@ConditionalOnProperty("almaraama.dev.invite-email")
class DevelopmentSeed implements ApplicationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(DevelopmentSeed.class);

    private final PlatformBootstrap bootstrap;
    private final String email;
    private final String phone;

    /**
     * @param bootstrap creates the platform admin and the demo organization
     * @param email     the developer's e-mail, from {@code ALMARAAMA_DEV_INVITE_EMAIL}
     * @param phone     the developer's mobile ({@code ALMARAAMA_DEV_INVITE_PHONE}, an Algerian test number by default)
     */
    DevelopmentSeed(PlatformBootstrap bootstrap, @Value("${almaraama.dev.invite-email}") String email,
                    @Value("${almaraama.dev.invite-phone:+213661234567}") String phone) {
        this.bootstrap = bootstrap;
        this.email = email;
        this.phone = phone;
    }

    /** Makes the developer platform admin and promoteur of the demo organization, and says where the link goes. */
    @Override
    public void run(ApplicationArguments args) {
        if (email.isBlank()) {
            return;
        }
        InvitationIssued issued = bootstrap.ensurePlatformAdmin(email, phone, "Amina", "Benali");
        bootstrap.ensureDemoOrganization(issued.personId());
        if (issued.existing()) {
            LOG.warn("DEV account {} exists: sign in with its password (or use \"forgot password\")",
                    LogMasking.email(email));
        } else {
            LOG.warn("DEV account {} created: open the \"set my password\" e-mail in Mailpit, http://127.0.0.1:8025",
                    LogMasking.email(email));
        }
    }
}
