package cloud.dcrivella.systemtests;

import static io.cucumber.junit.platform.engine.Constants.GLUE_PROPERTY_NAME;
import static io.cucumber.junit.platform.engine.Constants.PLUGIN_PROPERTY_NAME;

import org.junit.jupiter.api.Tag;
import org.junit.platform.suite.api.ConfigurationParameter;
import org.junit.platform.suite.api.IncludeEngines;
import org.junit.platform.suite.api.SelectClasspathResource;
import org.junit.platform.suite.api.Suite;

/**
 * Runs the complete machine-to-machine flow across the real services in an active runtime. No application component is replaced: the
 * authorization server issues the token and the resource server validates it before returning tasks.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@Suite
@Tag("e2e")
@IncludeEngines("cucumber")
@SelectClasspathResource("features")
@ConfigurationParameter(key = GLUE_PROPERTY_NAME, value = "cloud.dcrivella.systemtests")
@ConfigurationParameter(key = PLUGIN_PROPERTY_NAME, value = "pretty,summary")
class CucumberE2eSystemTest {
}
