package cloud.dcrivella.resourceserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.http.HttpHeaders.AUTHORIZATION;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import no.nav.security.mock.oauth2.MockOAuth2Server;
import org.junit.jupiter.api.AfterAll;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.http.HttpStatus;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Verifies the complete resource server with real Spring components and a simulated OAuth issuer and JWKS endpoint.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@SpringBootTest(classes = ResourceServerApplication.class)
@AutoConfigureMockMvc
class ResourceServerIntegrationTest {

    private static final String ISSUER = "default";
    private static final String AUDIENCE = "api://resource-server";
    private static final MockOAuth2Server OAUTH = new MockOAuth2Server();

    static {
        OAUTH.start();
    }

    private final MockMvc mockMvc;

    /**
     * Creates the integration test with the MockMvc client provided by Spring.
     *
     * @param mockMvc HTTP test client connected to the resource server context
     */
    ResourceServerIntegrationTest(MockMvc mockMvc) {
        this.mockMvc = mockMvc;
    }

    /**
     * Points issuer validation, JWKS retrieval and audience validation to the simulated OAuth server.
     *
     * @param registry Spring integration-test property registry
     */
    @DynamicPropertySource
    static void properties(DynamicPropertyRegistry registry) {
        registry.add("spring.security.oauth2.resourceserver.jwt.issuer-uri", () -> OAUTH.issuerUrl(ISSUER).toString());
        registry.add("spring.security.oauth2.resourceserver.jwt.jwk-set-uri", () -> OAUTH.jwksUrl(ISSUER).toString());
        registry.add("spring.security.oauth2.resourceserver.jwt.audience", () -> AUDIENCE);
    }

    /** Stops the simulated OAuth server after the integration test suite. */
    @AfterAll
    static void stopOAuthServers() {
        OAUTH.shutdown();
    }

    /** Verifies that a valid scoped token reaches the real task adapter and returns all three tasks. */
    @Test
    void acceptsAValidTokenAndUsesTheRealTaskAdapter() throws Exception {
        var response = mockMvc.perform(get("/tasks").header(AUTHORIZATION, bearer(issue(AUDIENCE, Map.of("scope", "api.read"), 3600))))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.OK.value());
        assertThat(response.getContentAsString()).contains("<h1>Tasks for subject-123:</h1>", "<li>Task 1</li>", "<li>Task 2</li>",
                "<li>Task 3</li>");
    }

    /** Verifies that a valid token without {@code api.read} is authenticated but forbidden. */
    @Test
    void rejectsATokenWithoutTheRequiredScope() throws Exception {
        var response = mockMvc.perform(get("/tasks").header(AUTHORIZATION, bearer(issue(AUDIENCE, Map.of(), 3600)))).andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.FORBIDDEN.value());
    }

    /** Verifies that a token issued for another resource audience is rejected. */
    @Test
    void rejectsATokenWithTheWrongAudience() throws Exception {
        var response = mockMvc
                .perform(get("/tasks").header(AUTHORIZATION, bearer(issue("api://another", Map.of("scope", "api.read"), 3600)))).andReturn()
                .getResponse();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    /** Verifies that issuer and signature validation independently reject untrusted tokens. */
    @Test
    void rejectsTokensWithAnInvalidIssuerOrSignature() throws Exception {
        for (String token : List.of(wrongIssuerToken(), invalidSignatureToken())) {
            var response = mockMvc.perform(get("/tasks").header(AUTHORIZATION, bearer(token))).andReturn().getResponse();

            assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
        }
    }

    /** Verifies that timestamp validation rejects an expired access token. */
    @Test
    void rejectsAnExpiredToken() throws Exception {
        var response = mockMvc.perform(get("/tasks").header(AUTHORIZATION, bearer(issue(AUDIENCE, Map.of("scope", "api.read"), -300))))
                .andReturn().getResponse();

        assertThat(response.getStatus()).isEqualTo(HttpStatus.UNAUTHORIZED.value());
    }

    /**
     * Issues a signed access token through the simulated OAuth server.
     *
     * @param audience token resource audience
     * @param claims additional token claims
     * @param expirySeconds token lifetime relative to the current time
     * @return the serialized JWT
     */
    private static String issue(String audience, Map<String, Object> claims, long expirySeconds) {
        return OAUTH.issueToken(ISSUER, "subject-123", audience, claims, expirySeconds).serialize();
    }

    /**
     * Creates a correctly signed token that carries an untrusted issuer.
     *
     * @return the serialized JWT
     */
    private static String wrongIssuerToken() {
        Map<String, Object> claims = claims();
        claims.put("iss", OAUTH.url("wrong-issuer").toString());
        return issue(AUDIENCE, claims, 3600);
    }

    /**
     * Corrupts a valid token signature without changing its claims.
     *
     * @return the serialized JWT with an invalid signature
     */
    private static String invalidSignatureToken() {
        String token = issue(AUDIENCE, Map.of("scope", "api.read"), 3600);
        int signatureStart = token.lastIndexOf('.') + 1;
        char replacement = token.charAt(signatureStart) == 'A' ? 'B' : 'A';
        return token.substring(0, signatureStart) + replacement + token.substring(signatureStart + 1);
    }

    /**
     * Creates the standard valid resource access claims used by negative scenarios.
     *
     * @return mutable claims for a valid token
     */
    private static Map<String, Object> claims() {
        Map<String, Object> claims = new LinkedHashMap<>();
        claims.put("aud", List.of(AUDIENCE));
        claims.put("scope", "api.read");
        claims.put("sub", "subject-123");
        return claims;
    }

    /**
     * Formats a serialized JWT for the HTTP Authorization header.
     *
     * @param token serialized access token
     * @return bearer authorization value
     */
    private static String bearer(String token) {
        return "Bearer " + token;
    }
}
