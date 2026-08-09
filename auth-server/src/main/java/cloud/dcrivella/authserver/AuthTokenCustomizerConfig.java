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
 * Customizes access tokens with each OAuth client's resource audience and API scopes.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@Configuration
@EnableConfigurationProperties(TokenAudienceProperties.class)
public class AuthTokenCustomizerConfig {

    /**
     * Adds the configured {@code aud} claim and removes OpenID Connect login scopes from access tokens.
     *
     * @param props audience mappings keyed by OAuth client identifier
     * @return the access token claims customizer
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

            Set<String> apiScopes = context.getAuthorizedScopes().stream().filter(scope -> !OidcScopes.OPENID.equals(scope))
                    .filter(scope -> !OidcScopes.PROFILE.equals(scope)).collect(Collectors.toCollection(java.util.TreeSet::new));

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
