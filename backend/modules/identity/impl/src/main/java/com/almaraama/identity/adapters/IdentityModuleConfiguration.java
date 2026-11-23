package com.almaraama.identity.adapters;

import com.almaraama.identity.application.IdentityProperties;
import com.almaraama.identity.domain.CommonPasswordList;
import com.almaraama.identity.domain.PasswordPolicy;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/** Wiring of the identity module's settings and of the plain-Java domain services. */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(IdentityProperties.class)
class IdentityModuleConfiguration {

    /** The password rules of ADR-030, with the embedded list of common passwords. */
    @Bean
    PasswordPolicy passwordPolicy(CommonPasswordList commonPasswords) {
        return new PasswordPolicy(commonPasswords);
    }
}
