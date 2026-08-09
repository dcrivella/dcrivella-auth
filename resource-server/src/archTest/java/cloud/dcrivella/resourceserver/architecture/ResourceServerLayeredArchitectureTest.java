package cloud.dcrivella.resourceserver.architecture;

import static com.tngtech.archunit.library.Architectures.layeredArchitecture;

import org.junit.jupiter.api.Test;

/**
 * Tests the dependency direction between resource server presentation, application, domain and infrastructure layers.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
class ResourceServerLayeredArchitectureTest {

    private static final String PRESENTATION = "Presentation";
    private static final String APPLICATION = "Application";
    private static final String DOMAIN = "Domain";
    private static final String INFRASTRUCTURE = "Infrastructure";

    /** Verifies that request handling follows the declared layered dependency boundaries. */
    @Test
    void layersShouldFollowTheDependencyDirection() {
        layeredArchitecture().consideringAllDependencies().layer(PRESENTATION).definedBy("cloud.dcrivella.resourceserver.presentation..")
                .layer(APPLICATION).definedBy("cloud.dcrivella.resourceserver.application..").layer(DOMAIN)
                .definedBy("cloud.dcrivella.resourceserver.domain..").layer(INFRASTRUCTURE)
                .definedBy("cloud.dcrivella.resourceserver.infrastructure..").whereLayer(PRESENTATION).mayNotBeAccessedByAnyLayer()
                .whereLayer(APPLICATION).mayOnlyBeAccessedByLayers(PRESENTATION).whereLayer(DOMAIN)
                .mayOnlyBeAccessedByLayers(APPLICATION, INFRASTRUCTURE, PRESENTATION).whereLayer(INFRASTRUCTURE)
                .mayNotBeAccessedByAnyLayer().because("request flow should remain Presentation -> Application -> Domain <- Infrastructure")
                .check(ResourceServerArchitectureClasses.PRODUCTION_CLASSES);
    }
}
