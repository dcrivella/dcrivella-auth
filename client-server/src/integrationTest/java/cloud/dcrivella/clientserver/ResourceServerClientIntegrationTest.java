package cloud.dcrivella.clientserver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.context.junit.jupiter.SpringJUnitConfig;

/**
 * Verifies the WebClient resource server adapter against an HTTP boundary controlled by the JDK HTTP server.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@SpringJUnitConfig(ResourceServerClientIntegrationTestConfiguration.class)
class ResourceServerClientIntegrationTest {

    private static final String ACCESS_TOKEN = "access-token";

    private final ResourceServerClient client;
    private final ResourceServerStub resourceServer;

    /**
     * Creates the integration test with the production adapter and its controlled HTTP server.
     *
     * @param client production resource server client
     * @param resourceServer HTTP stub that controls the external resource server boundary
     */
    ResourceServerClientIntegrationTest(ResourceServerClient client, ResourceServerStub resourceServer) {
        this.client = client;
        this.resourceServer = resourceServer;
    }

    /** Resets the HTTP stub before each test. */
    @BeforeEach
    void setUp() {
        resourceServer.reset();
    }

    /** Verifies that successful task responses are returned and carry the expected bearer token. */
    @Test
    void returnsSuccessfulResponsesAndSendsTheBearerToken() {
        // Given
        resourceServer.respondWith(200, "<ol><li>Task 1</li></ol>");

        // When
        String response = client.fetchTasks(ACCESS_TOKEN);

        // Then
        assertThat(response).isEqualTo("<ol><li>Task 1</li></ol>");
        assertThat(resourceServer.authorizationHeader()).isEqualTo("Bearer " + ACCESS_TOKEN);
    }

    /** Verifies the user-facing representation of a resource server unauthorized response. */
    @Test
    void explainsUnauthorizedResponses() {
        // Given
        resourceServer.respondWith(401, "invalid token");

        // When
        String response = client.fetchTasks(ACCESS_TOKEN);

        // Then
        assertThat(response).isEqualTo("<pre>Unauthorized (likely missing or invalid audience) [401]\nDetails: invalid token</pre>");
    }

    /** Verifies the user-facing representation of a resource server forbidden response. */
    @Test
    void explainsForbiddenResponses() {
        // Given
        resourceServer.respondWith(403, "");

        // When
        String response = client.fetchTasks(ACCESS_TOKEN);

        // Then
        assertThat(response).isEqualTo("<pre>Forbidden (grant api.read on the consent screen and sign in again) [403]</pre>");
    }

    /** Verifies that other resource server failures retain their status and response details. */
    @Test
    void preservesOtherServerErrors() {
        // Given
        resourceServer.respondWith(503, "temporarily unavailable");

        // When
        String response = client.fetchTasks(ACCESS_TOKEN);

        // Then
        assertThat(response).isEqualTo("<pre>Error [503]\nDetails: temporarily unavailable</pre>");
    }
}
