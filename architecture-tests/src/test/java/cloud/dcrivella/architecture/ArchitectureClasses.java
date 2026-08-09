package cloud.dcrivella.architecture;

import com.tngtech.archunit.core.domain.JavaClasses;
import com.tngtech.archunit.core.importer.ClassFileImporter;
import com.tngtech.archunit.core.importer.ImportOption;

/**
 * Provides production classes from all three applications to the global ArchUnit rules.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
final class ArchitectureClasses {

    static final JavaClasses PRODUCTION_CLASSES = new ClassFileImporter().withImportOption(ImportOption.Predefined.DO_NOT_INCLUDE_TESTS)
            .importPackages("cloud.dcrivella.authserver", "cloud.dcrivella.clientserver", "cloud.dcrivella.resourceserver");

    /** Prevents instantiation of this global ArchUnit class catalog. */
    private ArchitectureClasses() {
    }
}
