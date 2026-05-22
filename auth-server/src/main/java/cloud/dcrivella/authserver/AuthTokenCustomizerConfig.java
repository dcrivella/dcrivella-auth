package cloud.dcrivella.authserver;

import cloud.dcrivella.authserver.config.TokenAudienceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.core.endpoint.OAuth2ParameterNames;
import org.springframework.security.oauth2.core.oidc.OidcScopes;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Registers token customization for access tokens.
 * <p>
 * Reads {@link TokenAudienceProperties} (prefix {@code auth.token})
 * and, when a mapping exists for the current {@code clientId}, sets the JWT {@code aud}
 * claim to the configured list of audience values.
 * <p>
 * Access tokens are meant for resource servers, so OIDC login scopes such as
 * {@code openid} and {@code profile} are removed from the access token {@code scope}
 * claim. Those scopes remain part of the authorization request and ID token flow.
 * No change is made for ID tokens.
 */
@Configuration
@EnableConfigurationProperties(TokenAudienceProperties.class)
public class AuthTokenCustomizerConfig {

    /**
     * Customizes access token claims to include the configured {@code aud} claim
     * and expose only API scopes in the access token {@code scope} claim.
     */
    @Bean
    protected OAuth2TokenCustomizer<JwtEncodingContext> audienceTokenCustomizer(TokenAudienceProperties props) {
        return context -> {
            boolean isAccessToken = "access_token".equals(context.getTokenType().getValue());
            if (!isAccessToken) {
                return;
            }

            String clientId = context.getRegisteredClient().getClientId();
            Map<String, List<String>> perClient = props.getClientAudiences();
            List<String> clientAud = perClient.get(clientId);
            if (clientAud != null && !clientAud.isEmpty()) {
                context.getClaims().claim("aud", clientAud);
            }

            Set<String> apiScopes = context.getAuthorizedScopes().stream()
                    .filter(scope -> !OidcScopes.OPENID.equals(scope))
                    .filter(scope -> !OidcScopes.PROFILE.equals(scope))
                    .collect(Collectors.toCollection(java.util.TreeSet::new));

            context.getClaims().claims(claims -> {
                if (apiScopes.isEmpty()) {
                    claims.remove(OAuth2ParameterNames.SCOPE);
                } else {
                    claims.put(OAuth2ParameterNames.SCOPE, String.join(" ", apiScopes));
                }
            });
        };
    }
}
