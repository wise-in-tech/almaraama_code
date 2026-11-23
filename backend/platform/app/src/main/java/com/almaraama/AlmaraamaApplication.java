package com.almaraama;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.modulith.Modulithic;

/**
 * Entry point of the modular monolith (ADR-002). Each direct sub-package is a Spring Modulith application module,
 * mapped to its own PostgreSQL schema (ADR-003, CF-3).
 */
@Modulithic(systemName = "almaraama", sharedModules = "shared")
@SpringBootApplication
public class AlmaraamaApplication {

    /** Starts the modular monolith: every module's impl jar on the classpath is assembled here (ADR-023). */
    public static void main(String[] args) {
        SpringApplication.run(AlmaraamaApplication.class, args);
    }
}
