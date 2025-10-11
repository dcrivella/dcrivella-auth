package cloud.dcrivella.authserver.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;
import java.util.Map;

/**
 * Configuration properties for customizing the {@code aud} claim in issued access tokens.
 * <p>
 * These properties allow defining audience values per OAuth2 client (RegisteredClient) so that
 * the Authorization Server can mint access tokens targeted to specific Resource Servers.
 * The mapping is read by the token customizer and, when present for a client, is applied to the
 * token's {@code aud} claim as a list of strings.
 * <p>
 * Property prefix: {@code auth.token}
 * <ul>
 *   <li>{@code client-audiences} – Map of {@code clientId -> List<String> audiences}.
 *       Example:
 *       <pre>
 *       auth:
 *         token:
 *           client-audiences:
 *             client-server-pkce: ["api://resource-server"]
 *             client-m2m: ["api://resource-server"]
 *       </pre>
 *   </li>
 * </ul>
 * Notes:
 * <ul>
 *   <li>Values are opaque to OAuth 2.0; using an {@code api://...} convention is a common practice
 *       for readability. Ensure the Resource Server enforces the same audience value(s).</li>
 *   <li>If no entry exists for a given clientId, no {@code aud} is added by this customization.</li>
 * </ul>
 */
@ConfigurationProperties(prefix = "auth.token")
public class TokenAudienceProperties {
    private Map<String, List<String>> clientAudiences = Map.of();

    public Map<String, List<String>> getClientAudiences() {
        return clientAudiences == null ? Map.of() : clientAudiences;
    }

    public void setClientAudiences(Map<String, List<String>> clientAudiences) {
        this.clientAudiences = clientAudiences == null ? Map.of() : clientAudiences;
    }
}
