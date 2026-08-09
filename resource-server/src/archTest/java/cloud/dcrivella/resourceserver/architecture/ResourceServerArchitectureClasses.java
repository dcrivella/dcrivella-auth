package cloud.dcrivella.resourceserver.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Provides the production resource server classes shared by module-specific ArchUnit rules.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
final class ResourceServerArchitectureClasses {

    private static final ImportOption DO_NOT_INCLUDE_ARCH_TESTS = location -> !location.contains("/archTest/");

    static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .withImportOption(DO_NOT_INCLUDE_ARCH_TESTS).importPackages("cloud.dcrivella.resourceserver");

    /** Prevents instantiation of this ArchUnit class catalog. */
    private ResourceServerArchitectureClasses() {
    }
}
