package cloud.dcrivella.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.noClasses;

import com.tngtech.archunit.base.DescribedPredicate;
import com.tngtech.archunit.core.domain.JavaMethodCall;
import com.tngtech.archunit.library.GeneralCodingRules;
import java.util.Set;
import org.junit.jupiter.api.Test;
import org.springframework.context.annotation.Lazy;
import org.springframework.core.env.Environment;

/**
 * Tests shared Spring dependency injection and configuration access rules across the applications.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
class SpringArchitectureTest {

    /** Verifies that production dependencies are explicit through constructor or bean method injection. */
    @Test
    void productionClassesShouldNotUseFieldInjection() {
        GeneralCodingRules.NO_CLASSES_SHOULD_USE_FIELD_INJECTION
                .because("constructor injection keeps dependencies explicit and allows tests to instantiate classes without a container")
                .check(ArchitectureClasses.PRODUCTION_CLASSES);
    }

    /** Verifies that lazy injection does not hide circular application dependencies. */
    @Test
    void productionClassesShouldNotUseLazyInjection() {
        noClasses().should().dependOnClassesThat().areAssignableTo(Lazy.class)
                .because("lazy injection can hide circular dependencies that should be removed by design")
                .check(ArchitectureClasses.PRODUCTION_CLASSES);
    }

    /** Verifies that application configuration is bound through typed properties. */
    @Test
    void productionClassesShouldNotReadSpringEnvironmentDirectly() {
        noClasses().should().callMethodWhere(springEnvironmentPropertyRead())
                .because("application configuration should be bound through typed properties")
                .check(ArchitectureClasses.PRODUCTION_CLASSES);
    }

    /** Verifies that string case conversion does not depend on the host default locale. */
    @Test
    void classesShouldNotUseLocaleUnsafeStringCaseConversion() {
        noClasses().should().callMethod(String.class, "toLowerCase").orShould().callMethod(String.class, "toUpperCase")
                .because("case conversion should specify a locale such as Locale.ROOT").check(ArchitectureClasses.PRODUCTION_CLASSES);
    }

    /**
     * Describes Spring Environment property reads forbidden by the typed configuration rule.
     *
     * @return a predicate matching direct property reads from Spring Environment
     */
    private DescribedPredicate<JavaMethodCall> springEnvironmentPropertyRead() {
        return DescribedPredicate.describe("calls Environment.getProperty or Environment.getRequiredProperty",
                call -> Set.of("getProperty", "getRequiredProperty").contains(call.getTarget().getName())
                        && call.getTarget().getOwner().isAssignableTo(Environment.class));
    }
}
