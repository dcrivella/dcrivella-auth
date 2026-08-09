package cloud.dcrivella.architecture;

import static com.tngtech.archunit.lang.syntax.ArchRuleDefinition.classes;

import org.junit.jupiter.api.Test;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.RestController;

/**
 * Tests naming conventions for Spring application, configuration and controller types.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
class NamingConventionTest {

    /** Verifies that Spring Boot entry points end with {@code Application}. */
    @Test
    void applicationEntryPointsShouldEndWithApplication() {
        classes().that().areAnnotatedWith(SpringBootApplication.class).should().haveSimpleNameEndingWith("Application")
                .check(ArchitectureClasses.PRODUCTION_CLASSES);
    }

    /** Verifies that configuration classes end with {@code Config}. */
    @Test
    void configurationClassesShouldEndWithConfig() {
        classes().that().areAnnotatedWith(Configuration.class).should().haveSimpleNameEndingWith("Config")
                .check(ArchitectureClasses.PRODUCTION_CLASSES);
    }

    /** Verifies that typed configuration properties end with {@code Properties}. */
    @Test
    void configurationPropertiesShouldEndWithProperties() {
        classes().that().areAnnotatedWith(ConfigurationProperties.class).should().haveSimpleNameEndingWith("Properties")
                .check(ArchitectureClasses.PRODUCTION_CLASSES);
    }

    /** Verifies that MVC controllers end with {@code Controller}. */
    @Test
    void mvcControllersShouldEndWithController() {
        classes().that().areAnnotatedWith(Controller.class).should().haveSimpleNameEndingWith("Controller")
                .check(ArchitectureClasses.PRODUCTION_CLASSES);
    }

    /** Verifies that REST controllers end with {@code Controller}. */
    @Test
    void restControllersShouldEndWithController() {
        classes().that().areAnnotatedWith(RestController.class).should().haveSimpleNameEndingWith("Controller")
                .check(ArchitectureClasses.PRODUCTION_CLASSES);
    }
}
