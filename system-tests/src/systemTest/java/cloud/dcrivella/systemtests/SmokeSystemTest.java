package cloud.dcrivella.systemtests;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;

/**
 * Verifies essential availability and security capabilities without executing the complete machine-to-machine flow.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@Tag("smoke")
class SmokeSystemTest extends SystemTestSupport {

    /** Verifies authorization server discovery, issuer metadata and JWKS availability. */
    @Test
    void authorizationServerPublishesDiscoveryAndJwks() throws Exception {
        HttpResponse<String> discovery = get(AUTH_SERVER_URL, "/.well-known/openid-configuration");

        assertThat(discovery.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(discovery.body(), "$.issuer")).isEqualTo(EXPECTED_ISSUER);
        String advertisedJwksUri = JsonPath.read(discovery.body(), "$.jwks_uri");
        HttpRequest jwksRequest = HttpRequest.newBuilder(publicEndpointFor(advertisedJwksUri)).GET().build();
        HttpResponse<String> jwks = send(jwksRequest);

        assertThat(jwks.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(jwks.body(), "$.keys.length()")).isPositive();
    }

    /** Verifies that a protected client page redirects an anonymous caller to OAuth login. */
    @Test
    void clientServerRedirectsProtectedRequestsToOAuthLogin() throws Exception {
        HttpResponse<String> response = get(CLIENT_SERVER_URL, "/home");

        assertThat(response.statusCode()).isBetween(300, 399);
        assertThat(response.headers().firstValue("Location"))
                .hasValueSatisfying(location -> assertThat(location).contains("/oauth2/authorization/client-server-pkce-oidc"));
    }

    /** Verifies that the task endpoint rejects a request without a bearer token. */
    @Test
    void resourceServerRejectsRequestsWithoutABearerToken() throws Exception {
        assertThat(get(RESOURCE_SERVER_URL, "/tasks").statusCode()).isEqualTo(401);
    }
}
