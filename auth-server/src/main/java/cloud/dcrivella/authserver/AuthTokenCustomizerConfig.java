package cloud.dcrivella.authserver;

import cloud.dcrivella.authserver.config.TokenAudienceProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.token.JwtEncodingContext;
import org.springframework.security.oauth2.server.authorization.token.OAuth2TokenCustomizer;

import java.util.List;
import java.util.Map;

/**
 * Registers token customization for access tokens to populate the {@code aud} claim
 * based on per-client configuration.
 * <p>
 * Reads {@link TokenAudienceProperties} (prefix {@code auth.token})
 * and, when a mapping exists for the current {@code clientId}, sets the JWT {@code aud}
 * claim to the configured list of audience values. No change is made for ID tokens or
 * when no mapping exists.
 */
@Configuration
@EnableConfigurationProperties(TokenAudienceProperties.class)
public class AuthTokenCustomizerConfig {

    /**
     * Customizes access token claims to include the {@code aud} claim for clients
     * that have a configured audience mapping. The claim is set as a list of strings
     * to align with JWT conventions.
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
        };
    }
}
