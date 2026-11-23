package com.almaraama;

import org.junit.jupiter.api.Test;
import org.springframework.modulith.core.ApplicationModules;
import org.springframework.modulith.docs.Documenter;

/**
 * Checks the module graph (ADR-002, CF-1): no cycles, modules use each other only through their {@code api} and
 * {@code api.events} named interfaces, and only along the dependencies each module declares. A dependency that is
 * not declared fails the build.
 */
class ModularityTests {

    private static final ApplicationModules MODULES = ApplicationModules.of(AlmaraamaApplication.class);

    @Test
    void CF1_modulesRespectDeclaredDependencies() {
        MODULES.verify();
    }

    @Test
    void writesModuleDocumentation() {
        // Generated into target/spring-modulith-docs: PlantUML component diagrams and module canvases.
        new Documenter(MODULES)
                .writeModulesAsPlantUml()
                .writeIndividualModulesAsPlantUml()
                .writeModuleCanvases();
    }
}
