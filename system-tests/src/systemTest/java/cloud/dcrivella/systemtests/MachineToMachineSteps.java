package cloud.dcrivella.systemtests;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import io.cucumber.java.en.Given;
import io.cucumber.java.en.Then;
import io.cucumber.java.en.When;
import java.net.URLEncoder;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import org.assertj.core.api.InstanceOfAssertFactories;

/**
 * Cucumber steps that request a real machine token and use it against the real protected task endpoint.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
public class MachineToMachineSteps extends SystemTestSupport {

    private static final String CLIENT_ID = "client-m2m";
    private static final String CLIENT_SECRET = "secret2";
    private static final String AUDIENCE = "api://resource-server";

    private String clientSecret;
    private HttpResponse<String> tokenResponse;
    private String accessToken;
    private HttpResponse<String> tasksResponse;

    /** Selects the configured valid machine client credentials for the scenario. */
    @Given("the valid machine client credentials")
    public void theValidMachineClientCredentials() {
        clientSecret = CLIENT_SECRET;
    }

    /** Selects an invalid machine client secret for the rejection scenario. */
    @Given("invalid machine client credentials")
    public void invalidMachineClientCredentials() {
        clientSecret = "wrong-secret";
    }

    /** Requests an {@code api.read} access token through the client credentials grant. */
    @When("the machine client requests an api.read access token")
    public void theMachineClientRequestsAnAccessToken() throws Exception {
        tokenResponse = requestToken(clientSecret);
    }

    /** Verifies the issued token's issuer, subject, resource audience and API scope. */
    @Then("the authorization server issues a token for the resource server")
    public void theAuthorizationServerIssuesATokenForTheResourceServer() {
        assertThat(tokenResponse.statusCode()).isEqualTo(200);
        accessToken = JsonPath.read(tokenResponse.body(), "$.access_token");
        String claims = decodeClaims(accessToken);
        assertThat(JsonPath.<String>read(claims, "$.iss")).isEqualTo(EXPECTED_ISSUER);
        assertThat(JsonPath.<String>read(claims, "$.sub")).isEqualTo(CLIENT_ID);
        assertThat(JsonPath.<Object>read(claims, "$.aud")).satisfiesAnyOf(audience -> assertThat(audience).isEqualTo(AUDIENCE),
                audience -> assertThat(audience).asInstanceOf(InstanceOfAssertFactories.list(String.class)).contains(AUDIENCE));
        assertThat(JsonPath.<String>read(claims, "$.scope").split(" ")).contains("api.read");
    }

    /** Calls the protected task endpoint with the access token issued earlier in the scenario. */
    @When("the machine client calls the tasks endpoint with that token")
    public void theMachineClientCallsTheTasksEndpointWithThatToken() throws Exception {
        HttpRequest request = HttpRequest.newBuilder(endpoint(RESOURCE_SERVER_URL, "/tasks"))
                .header("Authorization", "Bearer " + accessToken).GET().build();
        tasksResponse = send(request);
    }

    /** Verifies that the resource server identifies the machine client and returns all three tasks. */
    @Then("the resource server returns the subject and all three tasks")
    public void theResourceServerReturnsTheSubjectAndAllThreeTasks() {
        assertThat(tasksResponse.statusCode()).isEqualTo(200);
        assertThat(tasksResponse.body()).contains("Tasks for " + CLIENT_ID, "<li>Task 1</li>", "<li>Task 2</li>", "<li>Task 3</li>");
    }

    /** Verifies that invalid machine credentials are rejected as an OAuth {@code invalid_client} error. */
    @Then("the authorization server rejects the machine credentials")
    public void theAuthorizationServerRejectsTheMachineCredentials() {
        assertThat(tokenResponse.statusCode()).isEqualTo(401);
        assertThat(JsonPath.<String>read(tokenResponse.body(), "$.error")).isEqualTo("invalid_client");
    }

    /**
     * Requests a machine access token using HTTP Basic client authentication.
     *
     * @param secret machine client secret
     * @return the token endpoint response
     * @throws Exception when the HTTP request cannot be completed
     */
    private static HttpResponse<String> requestToken(String secret) throws Exception {
        String credentials = Base64.getEncoder().encodeToString((CLIENT_ID + ":" + secret).getBytes(StandardCharsets.UTF_8));
        String body = "grant_type=client_credentials&scope=" + URLEncoder.encode("api.read", StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(endpoint(AUTH_SERVER_URL, "/oauth2/token"))
                .header("Authorization", "Basic " + credentials).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return send(request);
    }

    /**
     * Decodes the claims segment of a signed JWT without replacing runtime signature validation.
     *
     * @param token serialized JWT returned by the authorization server
     * @return decoded JSON claims
     * @throws IllegalArgumentException when the access token is not a signed JWT
     */
    private static String decodeClaims(String token) {
        String[] parts = token.split("\\.");
        if (parts.length != 3) {
            throw new IllegalArgumentException("Expected a signed JWT access token");
        }
        return new String(Base64.getUrlDecoder().decode(parts[1]), StandardCharsets.UTF_8);
    }
}
