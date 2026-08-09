package cloud.dcrivella.authserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Binds access token audiences by OAuth client identifier from the {@code auth.token} namespace.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@ConfigurationProperties(prefix = "auth.token")
public class TokenAudienceProperties {
    private Map<String, List<String>> clientAudiences = Map.of();

    /**
     * Returns the configured audiences for each OAuth client.
     *
     * @return an empty map when no client audience is configured
     */
    public Map<String, List<String>> getClientAudiences() {
        return clientAudiences == null ? Map.of() : clientAudiences;
    }

    /**
     * Replaces the audience mapping while normalizing a null configuration to an empty map.
     *
     * @param clientAudiences audiences keyed by OAuth client identifier
     */
    public void setClientAudiences(Map<String, List<String>> clientAudiences) {
        this.clientAudiences = clientAudiences == null ? Map.of() : clientAudiences;
    }
}
