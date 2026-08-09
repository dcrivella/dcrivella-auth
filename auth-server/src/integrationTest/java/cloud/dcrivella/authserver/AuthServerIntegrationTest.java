package cloud.dcrivella.authserver;

import static org.assertj.core.api.Assertions.assertThat;

import com.jayway.jsonpath.JsonPath;
import com.nimbusds.jwt.SignedJWT;
import java.net.CookieManager;
import java.net.CookiePolicy;
import java.net.HttpCookie;
import java.net.URI;
import java.net.URLEncoder;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.util.Base64;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.security.core.session.SessionInformation;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsent;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;

/**
 * Verifies the running authorization server and its machine-to-machine token contract.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@SpringBootTest(classes = AuthServerApplication.class, webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class AuthServerIntegrationTest {

    private static final String CLIENT_ID = "client-m2m";
    private static final String CLIENT_SECRET = "secret2";
    private static final String AUDIENCE = "api://resource-server";
    private static final String USERNAME = "user";
    private static final String PASSWORD = "pass";
    private static final String PLAYWRIGHT_USERNAME = "playwright";
    private static final String PLAYWRIGHT_PASSWORD = "playwright-pass";
    private static final String SESSION_COOKIE = "AUTH_SESSION";
    private static final String BROWSER_CLIENT_ID = "client-server-pkce";
    private static final String PLAYWRIGHT_CONSENT_SCOPE = "playwright.consent";
    private static final String PLAYWRIGHT_DENIAL_CLIENT_ID = "playwright-consent-denial";
    private static final Pattern CSRF_TOKEN = Pattern.compile("name=\"_csrf\"[^>]*value=\"([^\"]+)\"");

    private final HttpClient httpClient = HttpClient.newHttpClient();
    private final int port;
    private final SessionRegistry sessionRegistry;
    private final RegisteredClientRepository registeredClients;
    private final OAuth2AuthorizationConsentService authorizationConsents;

    /**
     * Creates the integration test with the random HTTP port assigned by Spring Boot.
     *
     * @param port local authorization server port
     * @param sessionRegistry authenticated-session registry provided by the application
     * @param registeredClients OAuth clients provided by the application
     * @param authorizationConsents stored authorization consent service
     */
    AuthServerIntegrationTest(@LocalServerPort int port, SessionRegistry sessionRegistry, RegisteredClientRepository registeredClients,
            OAuth2AuthorizationConsentService authorizationConsents) {
        this.port = port;
        this.sessionRegistry = sessionRegistry;
        this.registeredClients = registeredClients;
        this.authorizationConsents = authorizationConsents;
    }

    /** Verifies that OpenID Connect discovery advertises a reachable JWKS endpoint. */
    @Test
    void publishesOidcDiscoveryAndJwks() throws Exception {
        HttpResponse<String> discovery = get("/.well-known/openid-configuration");

        assertThat(discovery.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<String>read(discovery.body(), "$.issuer")).isEqualTo(baseUrl());
        String jwksUri = JsonPath.read(discovery.body(), "$.jwks_uri");

        HttpResponse<String> jwks = get(URI.create(jwksUri));
        assertThat(jwks.statusCode()).isEqualTo(200);
        assertThat(JsonPath.<Integer>read(jwks.body(), "$.keys.length()")).isPositive();
    }

    /** Verifies that valid machine credentials produce the expected subject, audience and API scope. */
    @Test
    void issuesAMachineTokenWithTheExpectedAudienceAndScope() throws Exception {
        HttpResponse<String> response = requestToken(CLIENT_SECRET);

        assertThat(response.statusCode()).isEqualTo(200);
        String accessToken = JsonPath.read(response.body(), "$.access_token");
        SignedJWT jwt = SignedJWT.parse(accessToken);

        assertThat(jwt.getJWTClaimsSet().getSubject()).isEqualTo(CLIENT_ID);
        assertThat(jwt.getJWTClaimsSet().getAudience()).containsExactly(AUDIENCE);
        assertThat(jwt.getJWTClaimsSet().getStringClaim("scope")).isEqualTo("api.read");
    }

    /** Verifies that the token endpoint rejects an invalid machine client secret. */
    @Test
    void rejectsInvalidMachineCredentials() throws Exception {
        HttpResponse<String> response = requestToken("wrong-secret");

        assertThat(response.statusCode()).isEqualTo(401);
        assertThat(JsonPath.<String>read(response.body(), "$.error")).isEqualTo("invalid_client");
    }

    /** Verifies the optional marker and isolated denial client used by real browser consent tests. */
    @Test
    void registersPlaywrightConsentSupport() {
        assertThat(registeredClients.findByClientId(BROWSER_CLIENT_ID)).isNotNull() //
                .satisfies(client -> assertThat(client.getScopes()).contains(PLAYWRIGHT_CONSENT_SCOPE));
        assertThat(registeredClients.findByClientId(PLAYWRIGHT_DENIAL_CLIENT_ID)).isNotNull() //
                .satisfies(client -> { //
                    assertThat(client.getScopes()).contains("api.read");
                    assertThat(client.getClientSettings().isRequireAuthorizationConsent()).isTrue();
                    assertThat(client.getClientSettings().isRequireProofKey()).isTrue();
                });
    }

    /** Verifies that a second login expires the user's previous session and sends it back to the login page. */
    @Test
    void allowsOnlyOneActiveSessionPerUser() throws Exception {
        AuthenticatedSession firstSession = loginUser();
        SessionInformation firstSessionInformation = sessionRegistry.getSessionInformation(firstSession.id());

        assertThat(firstSessionInformation).isNotNull();
        assertThat(firstSessionInformation.isExpired()).isFalse();

        AuthenticatedSession secondSession = loginUser();
        UserDetails user = sessionRegistry.getAllPrincipals().stream() //
                .filter(UserDetails.class::isInstance) //
                .map(UserDetails.class::cast) //
                .filter(principal -> USERNAME.equals(principal.getUsername())) //
                .findFirst() //
                .orElseThrow();

        assertThat(sessionRegistry.getSessionInformation(firstSession.id())).isNotNull().extracting(SessionInformation::isExpired)
                .isEqualTo(true);
        assertThat(sessionRegistry.getAllSessions(user, false)).extracting(SessionInformation::getSessionId)
                .containsExactly(secondSession.id());

        HttpResponse<String> expiredSessionResponse = get(firstSession.client(), "/");

        assertThat(expiredSessionResponse.statusCode()).isEqualTo(302);
        assertThat(expiredSessionResponse.headers().firstValue("Location"))
                .hasValueSatisfying(location -> assertThat(location).endsWith("/login"));
    }

    /** Verifies that browser automation does not expire the demonstration user's authenticated session. */
    @Test
    void keepsPlaywrightAndDemonstrationUserSessionsIsolated() throws Exception {
        AuthenticatedSession demonstrationSession = loginUser();
        AuthenticatedSession playwrightSession = loginUser(PLAYWRIGHT_USERNAME, PLAYWRIGHT_PASSWORD);

        assertThat(sessionRegistry.getSessionInformation(demonstrationSession.id())).isNotNull().extracting(SessionInformation::isExpired)
                .isEqualTo(false);
        assertThat(sessionRegistry.getSessionInformation(playwrightSession.id())).isNotNull().extracting(SessionInformation::isExpired)
                .isEqualTo(false);
    }

    /** Verifies that the same browser client stores independent consent for manual and automated principals. */
    @Test
    void storesPlaywrightAndDemonstrationUserConsentSeparately() {
        var browserClient = registeredClients.findByClientId(BROWSER_CLIENT_ID);
        assertThat(browserClient).isNotNull();

        var demonstrationConsent = OAuth2AuthorizationConsent.withId(browserClient.getId(), USERNAME).scope("profile").build();
        var playwrightConsent = OAuth2AuthorizationConsent.withId(browserClient.getId(), PLAYWRIGHT_USERNAME).scope("api.read").build();
        authorizationConsents.save(demonstrationConsent);
        authorizationConsents.save(playwrightConsent);

        try {
            assertThat(authorizationConsents.findById(browserClient.getId(), USERNAME)).isNotNull()
                    .satisfies(consent -> assertThat(consent.getScopes()).containsExactly("profile"));
            assertThat(authorizationConsents.findById(browserClient.getId(), PLAYWRIGHT_USERNAME)).isNotNull()
                    .satisfies(consent -> assertThat(consent.getScopes()).containsExactly("api.read"));
        } finally {
            authorizationConsents.remove(demonstrationConsent);
            authorizationConsents.remove(playwrightConsent);
        }
    }

    /**
     * Requests a client credentials token with the supplied machine client secret.
     *
     * @param secret machine client secret sent through HTTP Basic authentication
     * @return the token endpoint response
     * @throws Exception when the HTTP request cannot be completed
     */
    private HttpResponse<String> requestToken(String secret) throws Exception {
        String credentials = Base64.getEncoder().encodeToString((CLIENT_ID + ":" + secret).getBytes(StandardCharsets.UTF_8));
        String body = "grant_type=client_credentials&scope=" + URLEncoder.encode("api.read", StandardCharsets.UTF_8);
        HttpRequest request = HttpRequest.newBuilder(URI.create(baseUrl() + "/oauth2/token"))
                .header("Authorization", "Basic " + credentials).header("Content-Type", "application/x-www-form-urlencoded")
                .POST(HttpRequest.BodyPublishers.ofString(body)).build();
        return httpClient.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Authenticates the demonstration user with an isolated cookie store.
     *
     * @return the authenticated HTTP client and its current servlet session identifier
     * @throws Exception when the login page or form submission cannot be completed
     */
    private AuthenticatedSession loginUser() throws Exception {
        return loginUser(USERNAME, PASSWORD);
    }

    /**
     * Authenticates the selected user with an isolated cookie store.
     *
     * @param username resource-owner username
     * @param password resource-owner password
     * @return the authenticated HTTP client and its current servlet session identifier
     * @throws Exception when the login page or form submission cannot be completed
     */
    private AuthenticatedSession loginUser(String username, String password) throws Exception {
        CookieManager cookies = new CookieManager(null, CookiePolicy.ACCEPT_ALL);
        HttpClient client = HttpClient.newBuilder().cookieHandler(cookies).followRedirects(HttpClient.Redirect.NEVER).build();
        HttpResponse<String> loginPage = get(client, "/login");
        Matcher csrfMatcher = CSRF_TOKEN.matcher(loginPage.body());

        assertThat(loginPage.statusCode()).isEqualTo(200);
        assertThat(csrfMatcher.find()).isTrue();

        String form = "username=" + URLEncoder.encode(username, StandardCharsets.UTF_8) //
                + "&password=" + URLEncoder.encode(password, StandardCharsets.UTF_8) //
                + "&_csrf=" + URLEncoder.encode(csrfMatcher.group(1), StandardCharsets.UTF_8);
        HttpRequest loginRequest = HttpRequest.newBuilder(URI.create(baseUrl() + "/login")) //
                .header("Content-Type", "application/x-www-form-urlencoded") //
                .POST(HttpRequest.BodyPublishers.ofString(form)) //
                .build();
        HttpResponse<String> loginResponse = client.send(loginRequest, HttpResponse.BodyHandlers.ofString());

        assertThat(loginResponse.statusCode()).isEqualTo(302);
        String sessionId = cookies.getCookieStore().getCookies().stream() //
                .filter(cookie -> SESSION_COOKIE.equals(cookie.getName())) //
                .map(HttpCookie::getValue) //
                .findFirst() //
                .orElseThrow();
        return new AuthenticatedSession(client, sessionId);
    }

    /**
     * Performs a GET request against a path on the running authorization server.
     *
     * @param path authorization server path
     * @return the HTTP response
     * @throws Exception when the HTTP request cannot be completed
     */
    private HttpResponse<String> get(String path) throws Exception {
        return get(URI.create(baseUrl() + path));
    }

    /**
     * Performs a GET request against an absolute endpoint.
     *
     * @param uri endpoint to request
     * @return the HTTP response
     * @throws Exception when the HTTP request cannot be completed
     */
    private HttpResponse<String> get(URI uri) throws Exception {
        return get(httpClient, uri);
    }

    /**
     * Performs a GET request with the supplied isolated HTTP session.
     *
     * @param client HTTP client carrying the session cookies
     * @param path authorization server path
     * @return the HTTP response
     * @throws Exception when the HTTP request cannot be completed
     */
    private HttpResponse<String> get(HttpClient client, String path) throws Exception {
        return get(client, URI.create(baseUrl() + path));
    }

    /**
     * Performs a GET request with the supplied HTTP client.
     *
     * @param client HTTP client used to send the request
     * @param uri endpoint to request
     * @return the HTTP response
     * @throws Exception when the HTTP request cannot be completed
     */
    private HttpResponse<String> get(HttpClient client, URI uri) throws Exception {
        HttpRequest request = HttpRequest.newBuilder(uri).GET().build();
        return client.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Returns the random local URL assigned to the authorization server.
     *
     * @return the authorization server base URL
     */
    private String baseUrl() {
        return "http://localhost:" + port;
    }

    /** Holds an authenticated HTTP client and the servlet session registered for it. */
    private record AuthenticatedSession(HttpClient client, String id) {
    }
}
