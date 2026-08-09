package cloud.dcrivella.authserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.InMemoryOAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.OAuth2AuthorizationConsentService;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

/**
 * Configures the issuer advertised by the authorization server.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@Configuration
public class AuthorizationServerConfig {

    /**
     * Stores authorization consent separately for each registered client and resource-owner principal.
     *
     * <p>
     * Consent survives logout and token expiration, but this in-memory implementation loses it when the authorization-server process
     * restarts.
     *
     * @return the authorization server's ephemeral consent service
     */
    @Bean
    protected OAuth2AuthorizationConsentService authorizationConsentService() {
        return new InMemoryOAuth2AuthorizationConsentService();
    }

    /**
     * Builds the authorization server settings with the configured issuer when one is provided.
     *
     * @param issuer issuer URL shared with OAuth clients and resource servers
     * @return settings used by the authorization server endpoints
     */
    @Bean
    protected AuthorizationServerSettings authorizationServerSettings(@Value("${ISSUER_URL:}") String issuer) {
        AuthorizationServerSettings.Builder builder = AuthorizationServerSettings.builder();
        if (issuer != null && !issuer.isBlank()) {
            builder.issuer(issuer);
        }
        return builder.build();
    }
}
