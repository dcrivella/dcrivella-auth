package cloud.dcrivella.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

/**
 * Tests forbidden library and legacy Java EE dependencies across the three applications.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
class IllegalDependenciesTest {

    /** Verifies that application code uses Java or Spring APIs instead of Guava. */
    @Test
    void classesShouldNotUseGuava() {
        noClasses().should().dependOnClassesThat().resideInAPackage("com.google.common..")
                .because("the applications should prefer Java and Spring standard APIs").check(ArchitectureClasses.PRODUCTION_CLASSES);
    }

    /** Verifies that Spring Boot 4 code uses Jakarta namespaces instead of legacy Java EE APIs. */
    @Test
    void classesShouldNotUseLegacyJavaEeApis() {
        noClasses().should().dependOnClassesThat()
                .resideInAnyPackage("javax.annotation..", "javax.inject..", "javax.persistence..", "javax.servlet..", "javax.transaction..",
                        "javax.validation..")
                .because("Spring Boot 4 applications should use the corresponding Jakarta APIs")
                .check(ArchitectureClasses.PRODUCTION_CLASSES);
    }
}
