package com.almaraama;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;
import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.core.importer.ImportOption;
import com.tngtech.archunit.junit.AnalyzeClasses;
import com.tngtech.archunit.junit.ArchTest;
import com.tngtech.archunit.lang.ArchRule;

/**
 * Hexagonal architecture inside each module (ADR-023), and the loose-coupling rules that Spring Modulith does not
 * check. The domain and the contracts use allow-lists: a new technology cannot reach them unnoticed. Rules allow
 * empty packages so they hold from the empty skeleton onwards.
 * <p>
 * {@code package-info} classes are excluded where they only carry Modulith metadata annotations.
 */
@AnalyzeClasses(packages = "com.almaraama", importOptions = ImportOption.DoNotIncludeTests.class)
class ArchitectureTests {

    private static final String PACKAGE_INFO = "package-info";

    /**
     * CF-8: the domain is plain Java. It knows its own model, its own contract and the shared kernel. The only
     * technology it may touch is the standard JPA annotations on aggregates (section 7 of the architecture):
     * no Spring, no Hibernate-specific types, no web, and above all no external-system client.
     */
    @ArchTest
    static final ArchRule CF8_domainIsTechnologyFree = classes()
            .that().resideInAPackage("com.almaraama.*.domain..")
            .and().doNotHaveSimpleName(PACKAGE_INFO)
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "java..", "org.jspecify..", "jakarta.persistence..",
                    "com.almaraama.shared..", "com.almaraama.*.domain..", "com.almaraama.*.api..")
            .allowEmptyShould(true);

    /**
     * Use cases orchestrate the domain through ports. They may use Spring for transactions and wiring, but never a
     * persistence, web or remote-call technology: that is the adapters' job.
     */
    @ArchTest
    static final ArchRule applicationUsesPortsNotTechnology = noClasses()
            .that().resideInAPackage("com.almaraama.*.application..")
            .should().dependOnClassesThat().resideInAnyPackage(
                    "com.almaraama.*.adapters..",
                    "jakarta.persistence..", "org.hibernate..", "org.springframework.data..",
                    "org.springframework.jdbc..", "org.springframework.web..", "jakarta.servlet..",
                    "java.net.http..", "java.sql..")
            .allowEmptyShould(true);

    /**
     * CF-1, CF-2, CF-5: a contract is plain Java records and interfaces: typed IDs, DTOs, events. No entity, no
     * framework type, no other module's internals.
     */
    @ArchTest
    static final ArchRule CF1_apiIsAPlainContract = classes()
            .that().resideInAPackage("com.almaraama.*.api..")
            .and().doNotHaveSimpleName(PACKAGE_INFO)
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "java..", "org.jspecify..", "jakarta.validation..",
                    "com.almaraama.shared..", "com.almaraama.*.api..")
            .allowEmptyShould(true);

    /**
     * ADR-027: Apache POI and the hardened reader of {@code platform/spreadsheet} are used by adapters only; the
     * domain and the use cases see lines already read, through their own ports.
     */
    @ArchTest
    static final ArchRule ADR027_spreadsheetsOnlyInAdapters = noClasses()
            .that().resideOutsideOfPackages("com.almaraama.*.adapters..", "com.almaraama.spreadsheet..")
            .should().dependOnClassesThat().resideInAnyPackage("org.apache.poi..", "com.almaraama.spreadsheet..")
            .allowEmptyShould(true);

    /** CF-9: the shared kernel is plain Java and depends on no module. */
    @ArchTest
    static final ArchRule CF9_sharedKernelIsPlainJava = classes()
            .that().resideInAPackage("com.almaraama.shared..")
            .and().doNotHaveSimpleName(PACKAGE_INFO)
            .should().onlyDependOnClassesThat().resideInAnyPackage(
                    "java..", "org.jspecify..", "com.almaraama.shared..")
            .allowEmptyShould(true);

    /** CF-11: the perimeter is passed as a parameter, never read from the security context in business code. */
    @ArchTest
    static final ArchRule CF11_noSecurityContextInBusinessCode = noClasses()
            .that().resideOutsideOfPackages("com.almaraama.*.adapters.in..", "com.almaraama.app..")
            .should().dependOnClassesThat().haveFullyQualifiedName(
                    "org.springframework.security.core.context.SecurityContextHolder")
            .allowEmptyShould(true);
}
