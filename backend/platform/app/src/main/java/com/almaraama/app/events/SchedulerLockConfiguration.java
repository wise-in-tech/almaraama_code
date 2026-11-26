package com.almaraama.app.events;

import javax.sql.DataSource;
import net.javacrumbs.shedlock.core.LockProvider;
import net.javacrumbs.shedlock.provider.jdbctemplate.JdbcTemplateLockProvider;
import net.javacrumbs.shedlock.spring.annotation.EnableSchedulerLock;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Locks of the scheduled jobs in the database (ShedLock, ADR-036): one instance runs a job at a time, whatever the
 * number of application instances. Table {@code platform.shedlock}, time of the database.
 */
@Configuration(proxyBeanMethods = false)
@EnableSchedulerLock(defaultLockAtMostFor = "PT30M")
class SchedulerLockConfiguration {

    /** The lock provider on {@code platform.shedlock}. */
    @Bean
    LockProvider lockProvider(DataSource dataSource) {
        return new JdbcTemplateLockProvider(JdbcTemplateLockProvider.Configuration.builder()
                .withJdbcTemplate(new JdbcTemplate(dataSource))
                .withTableName("platform.shedlock")
                .usingDbTime()
                .build());
    }
}
