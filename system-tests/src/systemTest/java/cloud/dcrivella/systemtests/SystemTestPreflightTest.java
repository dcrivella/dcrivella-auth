package cloud.dcrivella.systemtests;

import static org.assertj.core.api.Assertions.assertThat;

import java.net.URI;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Checks system-test configuration and service reachability before smoke or end-to-end scenarios run. The preflight never starts, stops or
 * changes the selected runtime.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@Tag("preflight")
class SystemTestPreflightTest extends SystemTestSupport {

    /** Verifies that application URLs and the expected issuer are absolute HTTP endpoints. */
    @Test
    void configuredValuesAreAbsoluteHttpEndpoints() {
        assertHttpEndpoint("authorization server", AUTH_SERVER_URL);
        assertHttpEndpoint("client server", CLIENT_SERVER_URL);
        assertHttpEndpoint("resource server", RESOURCE_SERVER_URL);
        assertHttpEndpoint("expected issuer", EXPECTED_ISSUER);
    }

    /** Verifies that an authorization server process is reachable before protocol assertions begin. */
    @Test
    void authorizationServerIsReachable() {
        assertReachable("authorization server", AUTH_SERVER_URL, "/.well-known/openid-configuration");
    }

    /** Verifies that a client server process is reachable before redirect assertions begin. */
    @Test
    void clientServerIsReachable() {
        assertReachable("client server", CLIENT_SERVER_URL, "/");
    }

    /** Verifies that a resource server process is reachable before bearer-token assertions begin. */
    @Test
    void resourceServerIsReachable() {
        assertReachable("resource server", RESOURCE_SERVER_URL, "/tasks");
    }

    /**
     * Validates the syntax required for a system-test endpoint.
     *
     * @param name endpoint role shown in assertion failures
     * @param value configured endpoint value
     */
    private static void assertHttpEndpoint(String name, String value) {
        URI uri = URI.create(value);
        assertThat(uri.getScheme()).as("%s URL scheme", name).isIn("http", "https");
        assertThat(uri.getHost()).as("%s URL host", name).isNotBlank();
        assertThat(uri.isAbsolute()).as("%s URL must be absolute", name).isTrue();
    }

    /**
     * Confirms that one configured application responds and provides an actionable failure when it is absent.
     *
     * @param name application role shown in assertion failures
     * @param baseUrl externally reachable application URL
     * @param path inexpensive endpoint used only for reachability
     */
    private static void assertReachable(String name, String baseUrl, String path) {
        try {
            HttpResponse<String> response = get(baseUrl, path);
            assertThat(response.statusCode()).as("HTTP status returned by the %s", name).isBetween(100, 599);
        } catch (Exception ex) {
            throw new AssertionError(
                    "The " + name + " is not reachable at " + baseUrl
                            + ". Start the selected Compose or k3d runtime before running system tests; the preflight does not start it.",
                    ex);
        }
    }
}
