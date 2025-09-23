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

@Configuration
public class ClientStoreConfig {

    private static final String POSTMAN_REDIRECT_URI = "https://oauth.pstmn.io/v1/callback";

    @Bean
    protected RegisteredClientRepository registeredClientRepository() {
        // Web confidential client - keeps its secret
        RegisteredClient postmanConfidential = RegisteredClient.withId(UUID.randomUUID().toString())
                .clientId("client-postman-confidential") //
                .clientSecret("{noop}secret1") //
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC) //
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE) //
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN) //
                .redirectUri(POSTMAN_REDIRECT_URI) //
                .scope(OidcScopes.OPENID) //
                .scope(OidcScopes.PROFILE) //
                .clientSettings(ClientSettings.builder() //
                        .requireAuthorizationConsent(false) // user consent not required
                        .requireProofKey(false) // confidential client
                        .build()) //
                .tokenSettings(TokenSettings.builder() //
                        .accessTokenTimeToLive(Duration.ofMinutes(5)) // default: 5m
                        .refreshTokenTimeToLive(Duration.ofMinutes(60)) // default: 60m
                        .reuseRefreshTokens(false) // rotate refresh tokens
                        .build()) //
                .build();

        // PKCE Postman client - no secret + NONE auth method
        RegisteredClient pkcePostmanClient = RegisteredClient.withId(UUID.randomUUID().toString()) //
                .clientId("client-server-postman-pkce") //
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE) //
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE) //
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN) //
                .redirectUri(POSTMAN_REDIRECT_URI) //
                .scope(OidcScopes.OPENID) //
                .scope(OidcScopes.PROFILE) //
                .clientSettings(ClientSettings.builder() //
                        .requireAuthorizationConsent(true) //
                        .requireProofKey(true) // enforce PKCE
                        .build()) //
                .tokenSettings(TokenSettings.builder() //
                        .accessTokenTimeToLive(Duration.ofMinutes(5)) // default: 5m
                        .refreshTokenTimeToLive(Duration.ofMinutes(60)) // default: 60m
                        .reuseRefreshTokens(false) // rotate refresh tokens
                        .build()) //
                .build();

        // PKCE(Proof Key for Code Exchange) client - no secret + NONE auth method
        RegisteredClient pkceClient = RegisteredClient.withId(UUID.randomUUID().toString()) //
                .clientId("client-server-pkce") //
                .clientAuthenticationMethod(ClientAuthenticationMethod.NONE) //
                .authorizationGrantType(AuthorizationGrantType.AUTHORIZATION_CODE) //
                .authorizationGrantType(AuthorizationGrantType.REFRESH_TOKEN) //
                // No need to add http://auth-server:9000/... as redirect login/logout URIs because redirects happen in the browser, not inside the containers.
                .redirectUri("http://localhost:8080/login/oauth2/code/client-server-pkce-oidc") //
                .postLogoutRedirectUri("http://localhost:8080/") //
                .scope(OidcScopes.OPENID) //
                .scope(OidcScopes.PROFILE) //
                .clientSettings(ClientSettings.builder() //
                        .requireAuthorizationConsent(true) //
                        .requireProofKey(true) // enforce PKCE
                        .build()) //
                .tokenSettings(TokenSettings.builder() //
                        .accessTokenTimeToLive(Duration.ofMinutes(5)) // default: 5m
                        .refreshTokenTimeToLive(Duration.ofMinutes(60)) // default: 60m
                        .reuseRefreshTokens(false) // rotate refresh tokens
                        .build()) //
                .build();

        // Machine-to-Machine client (client_credentials) – PKCE not used here
        RegisteredClient machineClient = RegisteredClient.withId(UUID.randomUUID().toString()) //
                .clientId("client-m2m") //
                .clientSecret("{noop}secret2") //
                .clientAuthenticationMethod(ClientAuthenticationMethod.CLIENT_SECRET_BASIC) //
                .authorizationGrantType(AuthorizationGrantType.CLIENT_CREDENTIALS) //
                .scope("api.read") //
                .tokenSettings(TokenSettings.builder() //
                        .accessTokenTimeToLive(java.time.Duration.ofMinutes(5)) //
                        .build()) //
                .build();

        return new InMemoryRegisteredClientRepository(postmanConfidential, pkceClient, pkcePostmanClient,
                machineClient);
    }
}
