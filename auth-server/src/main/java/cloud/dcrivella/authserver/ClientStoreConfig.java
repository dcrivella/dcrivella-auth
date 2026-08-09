package cloud.dcrivella.authserver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.AuthorizationGrantType;
import org.springframework.security.oauth2.core.ClientAuthenticationMethod;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.client.InMemoryRegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClient;
import org.springframework.security.oauth2.server.authorization.client.RegisteredClientRepository;
import org.springframework.security.oauth2.server.authorization.settings.ClientSettings;
import org.springframework.security.oauth2.server.authorization.settings.TokenSettings;

import java.time.Duration;
import java.util.UUID;

/**
 * Registers the OAuth clients used by the browser, Postman and machine-to-machine examples.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@Configuration
public class ClientStoreConfig {

    private static final String POSTMAN_REDIRECT_URI = "https://oauth.pstmn.io/v1/callback";
    private static final String SCOPE_OFFLINE_ACCESS = "offline_access";
    private static final String SCOPE_API_READ = "api.read";
    private static final String SCOPE_PLAYWRIGHT_CONSENT = "playwright.consent";
    private static final String PLAYWRIGHT_DENIAL_REDIRECT_URI = "http://localhost:8080/login";

    /**
     * Creates the in-memory client registry with authorization code, PKCE and client credentials clients.
     *
     * @return the registered OAuth client repository
     */
    @Bean
    protected RegisteredClientRepository registeredClientRepository() {
        // Web confidential client - keeps its secret
        RegisteredClient postmanConfidential = RegisteredClient.withId(UUID.randomUUID().toString()).clientId("client-postman-confidential") //
                .clientSecret("{noop}secret1") //
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC) //
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE) //
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN) //
                .redirectUri(POSTMAN_REDIRECT_URI) //
                .scope(OidcScopes.OPENID) // enable OIDC login and ID token issuance
                .scope(OidcScopes.PROFILE) // allow profile claims such as name, given_name and family_name
                .scope(SCOPE_OFFLINE_ACCESS) // allow issuing refresh tokens after user consent
                .scope(SCOPE_API_READ) // allow calling the resource-server
                .clientSettings(ClientSettings.builder() //
                        .requireAuthorizationConsent(false) // user consent not required
                        .requireProofKey(false) // confidential client
                        .build()) //
                .tokenSettings(TokenSettings.builder() //
                        .accessTokenTimeToLive(Duration.ofMinutes(5)) // default: 5m
                        .refreshTokenTimeToLive(Duration.ofMinutes(60)) // refresh token expires after 60 minutes
                        .reuseRefreshTokens(false) // issue a new refresh token on refresh and invalidate the old one
                        .build()) //
                .build();

        // Postman authorization-code client: confidential client + PKCE.
        RegisteredClient pkcePostmanClient = RegisteredClient.withId(UUID.randomUUID().toString()) //
                .clientId("client-server-postman-pkce") //
                .clientSecret("{noop}secret-postman-pkce") //
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC) //
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE) //
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN) //
                .redirectUri(POSTMAN_REDIRECT_URI) //
                .scope(OidcScopes.OPENID) // enable OIDC login and ID token issuance
                .scope(OidcScopes.PROFILE) // allow profile claims such as name, given_name and family_name
                .scope(SCOPE_OFFLINE_ACCESS) // allow issuing refresh tokens after user consent
                .scope(SCOPE_API_READ) // allow calling the resource-server
                .clientSettings(ClientSettings.builder() //
                        .requireAuthorizationConsent(true) //
                        .requireProofKey(true) // enforce PKCE
                        .build()) //
                .tokenSettings(TokenSettings.builder() //
                        .accessTokenTimeToLive(Duration.ofMinutes(5)) // default: 5m
                        .refreshTokenTimeToLive(Duration.ofMinutes(60)) // refresh token expires after 60 minutes
                        .reuseRefreshTokens(false) // issue a new refresh token on refresh and invalidate the old one
                        .build()) //
                .build();

        // Server-side web client: confidential client + PKCE.
        RegisteredClient pkceClient = RegisteredClient.withId(UUID.randomUUID().toString()) //
                .clientId("client-server-pkce") //
                .clientSecret("{noop}secret-client-server") //
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC) //
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE) //
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN) //
                // No need to add http://auth-server:9000/... as redirect login/logout URIs because redirects happen in the browser, not
                // inside the containers.
                .redirectUri("http://localhost:8080/login/oauth2/code/client-server-pkce-oidc") //
                .postLogoutRedirectUri("http://localhost:8080/") //
                .scope(OidcScopes.OPENID) // enable OIDC login and ID token issuance
                .scope(OidcScopes.PROFILE) // allow profile claims such as name, given_name and family_name
                .scope(SCOPE_OFFLINE_ACCESS) // allow issuing refresh tokens after user consent
                .scope(SCOPE_API_READ) // allow calling the resource-server
                .scope(SCOPE_PLAYWRIGHT_CONSENT) // keep real browser consent checks repeatable
                .clientSettings(ClientSettings.builder() //
                        .requireAuthorizationConsent(true) //
                        .requireProofKey(true) // enforce PKCE
                        .build()) //
                .tokenSettings(TokenSettings.builder() //
                        .accessTokenTimeToLive(Duration.ofMinutes(5)) // default: 5m
                        .refreshTokenTimeToLive(Duration.ofMinutes(60)) // refresh token expires after 60 minutes
                        .reuseRefreshTokens(false) // issue a new refresh token on refresh and invalidate the old one
                        .build()) //
                .build();

        RegisteredClient playwrightDenialClient = RegisteredClient.withId(UUID.randomUUID().toString()) //
                .clientId("playwright-consent-denial") //
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE) //
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE) //
                .redirectUri(PLAYWRIGHT_DENIAL_REDIRECT_URI) //
                .scope(OidcScopes.OPENID) //
                .scope(SCOPE_API_READ) //
                .clientSettings(ClientSettings.builder() //
                        .requireAuthorizationConsent(true) //
                        .requireProofKey(true) //
                        .build()) //
                .build();

        // Machine-to-Machine client (client_credentials) – PKCE not used here
        RegisteredClient machineClient = RegisteredClient.withId(UUID.randomUUID().toString()) //
                .clientId("client-m2m") //
                .clientSecret("{noop}secret2") //
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC) //
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS) //
                .scope(SCOPE_API_READ) // allow calling the resource-server
                .tokenSettings(TokenSettings.builder() //
                        .accessTokenTimeToLive(Duration.ofMinutes(5)) //
                        .build()) //
                .build();

        return new InMemoryRegisteredClientRepository(postmanConfidential, pkceClient, pkcePostmanClient, playwrightDenialClient,
                machineClient);
    }
}
