package cloud.dcrivella.clientserver;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Set;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.registration.ClientRegistration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.OidcIdToken;
import org.springframework.security.oauth2.core.oidc.user.DefaultOidcUser;
import org.springframework.ui.ExtendedModelMap;

/**
 * Tests home page orchestration with the resource server client port isolated by Mockito.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@ExtendWith(MockitoExtension.class)
class HomeControllerTest {

    @Mock
    private ResourceServerClient resourceServerClient;

    private HomeController controller;

    /** Creates the controller explicitly with its mocked port before each test. */
    @BeforeEach
    void setUp() {
        controller = new HomeController(resourceServerClient);
    }

    /** Verifies that the public root path redirects to the home page. */
    @Test
    void redirectsTheRootPathToHome() {
        assertThat(controller.index()).isEqualTo("redirect:/home");
    }

    /** Verifies that an authorized user sees token data, claims and tasks returned by the client port. */
    @Test
    void rendersTokensClaimsAndTasksFromTheResourceServerPort() {
        // Given
        OAuth2AuthorizedClient authorizedClient = authorizedClient();
        DefaultOidcUser user = oidcUser();
        ExtendedModelMap model = new ExtendedModelMap();
        given(resourceServerClient.fetchTasks("access-token")).willReturn("<ol><li>Task 1</li></ol>");

        // When
        String view = controller.home(authorizedClient, user, model);

        // Then
        assertThat(view).isEqualTo("home");
        assertThat(model).containsEntry("userName", "Test User").containsEntry("accessTokenValue", "access-token")
                .containsEntry("refreshTokenValue", "refresh-token").containsEntry("idTokenValue", "id-token")
                .containsEntry("tasksResponse", "<ol><li>Task 1</li></ol>")
                .containsEntry("accessTokenClaimsJson", "<unable to parse access token claims>");
        assertThat(model.get("claimsJson").toString()).contains("Test User", "subject-123");
        verify(resourceServerClient).fetchTasks("access-token");
    }

    /** Verifies that an absent authorized client renders an empty home page without calling the task port. */
    @Test
    void rendersTheHomeViewWithoutCallingThePortWhenNoAuthorizedClientExists() {
        // Given
        ExtendedModelMap model = new ExtendedModelMap();

        // When
        String view = controller.home(null, null, model);

        // Then
        assertThat(view).isEqualTo("home");
        assertThat(model).containsEntry("userName", null).containsEntry("tasksResponse", null);
        verifyNoInteractions(resourceServerClient);
    }

    /** Verifies that refreshing returns the current protected tasks without exposing the access token. */
    @Test
    void refreshesTheTaskFragmentWithTheAuthorizedClient() {
        given(resourceServerClient.fetchTasks("access-token")).willReturn("<ol><li>Task 1</li></ol>");

        ResponseEntity<String> response = controller.refreshTasks(authorizedClient());

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.OK);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).isEqualTo("<ol><li>Task 1</li></ol>");
        verify(resourceServerClient).fetchTasks("access-token");
    }

    /** Verifies that refreshing without an authorized client fails safely and does not call the task port. */
    @Test
    void rejectsTaskRefreshWhenNoAuthorizedClientExists() {
        ResponseEntity<String> response = controller.refreshTasks(null);

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.UNAUTHORIZED);
        assertThat(response.getHeaders().getContentType()).isEqualTo(MediaType.TEXT_HTML);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("no-store");
        assertThat(response.getBody()).isEqualTo("<p>Tasks could not be refreshed. Please sign in again.</p>");
        verifyNoInteractions(resourceServerClient);
    }

    /**
     * Builds an authorized OAuth client fixture with access and refresh tokens.
     *
     * @return the authorized client fixture
     */
    private static OAuth2AuthorizedClient authorizedClient() {
        Instant issuedAt = Instant.parse("2026-08-08T10:00:00Z");
        OAuth2AccessToken accessToken = new OAuth2AccessToken(OAuth2AccessToken.TokenType.BEARER, "access-token", issuedAt,
                issuedAt.plusSeconds(300), Set.of("api.read"));
        OAuth2RefreshToken refreshToken = new OAuth2RefreshToken("refresh-token", issuedAt, issuedAt.plusSeconds(3600));
        return new OAuth2AuthorizedClient(clientRegistration(), "subject-123", accessToken, refreshToken);
    }

    /**
     * Builds the OAuth client registration used by the controller fixture.
     *
     * @return the client registration fixture
     */
    private static ClientRegistration clientRegistration() {
        return ClientRegistration.withRegistrationId("test-client").clientId("client-server-pkce").clientSecret("secret-client-server")
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC)
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE).redirectUri("http://localhost/callback")
                .scope("openid", "profile", "api.read").authorizationUri("http://issuer/oauth2/authorize")
                .tokenUri("http://issuer/oauth2/token").jwkSetUri("http://issuer/oauth2/jwks").build();
    }

    /**
     * Builds an authenticated OpenID Connect user fixture.
     *
     * @return the OpenID Connect user fixture
     */
    private static DefaultOidcUser oidcUser() {
        Instant issuedAt = Instant.parse("2026-08-08T10:00:00Z");
        OidcIdToken idToken = new OidcIdToken("id-token", issuedAt, issuedAt.plusSeconds(300),
                Map.of("sub", "subject-123", "name", "Test User"));
        return new DefaultOidcUser(List.of(new SimpleGrantedAuthority("ROLE_USER")), idToken);
    }
}
