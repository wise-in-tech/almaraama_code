package com.almaraama.app.altcha;

import java.util.HashSet;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.web.servlet.FilterRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.Ordered;

/** Registers the ALTCHA filter after the rate limiter, before security and the modules (ADR-032). */
@Configuration(proxyBeanMethods = false)
@EnableConfigurationProperties(AltchaProperties.class)
class AltchaConfiguration {

    /** The filter on every request; it checks only the protected paths of {@link AltchaProperties}. */
    @Bean
    FilterRegistrationBean<AltchaFilter> altchaFilter(AltchaService altcha, AltchaProperties properties) {
        FilterRegistrationBean<AltchaFilter> registration =
                new FilterRegistrationBean<>(new AltchaFilter(altcha, new HashSet<>(properties.protectedPaths())));
        registration.setOrder(Ordered.HIGHEST_PRECEDENCE + 20);
        return registration;
    }
}
