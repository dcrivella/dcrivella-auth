package cloud.dcrivella.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import org.junit.jupiter.api.Test;

/**
 * Tests that each deployable application remains independent from the other application modules.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
class ModuleArchitectureTest {

    private static final String AUTH_SERVER = "cloud.dcrivella.authserver..";
    private static final String CLIENT_SERVER = "cloud.dcrivella.clientserver..";
    private static final String RESOURCE_SERVER = "cloud.dcrivella.resourceserver..";

    /** Verifies that the authorization server does not depend on either consumer application. */
    @Test
    void authorizationServerShouldNotDependOnOtherApplications() {
        assertModuleDoesNotDependOn(AUTH_SERVER, CLIENT_SERVER, RESOURCE_SERVER);
    }

    /** Verifies that the OAuth client does not depend on either server implementation. */
    @Test
    void clientServerShouldNotDependOnOtherApplications() {
        assertModuleDoesNotDependOn(CLIENT_SERVER, AUTH_SERVER, RESOURCE_SERVER);
    }

    /** Verifies that the resource server does not depend on the authorization or client server implementation. */
    @Test
    void resourceServerShouldNotDependOnOtherApplications() {
        assertModuleDoesNotDependOn(RESOURCE_SERVER, AUTH_SERVER, CLIENT_SERVER);
    }

    /**
     * Checks one application package against forbidden target application packages.
     *
     * @param sourcePackage application package being checked
     * @param targetPackages application packages that must remain independent
     */
    private void assertModuleDoesNotDependOn(String sourcePackage, String... targetPackages) {
        noClasses().that().resideInAPackage(sourcePackage).should().dependOnClassesThat().resideInAnyPackage(targetPackages)
                .because("each application should remain independently buildable and deployable")
                .check(ArchitectureClasses.PRODUCTION_CLASSES);
    }
}
