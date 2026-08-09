package cloud.dcrivella.resourceserver.infrastructure.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds the resource audience expected in bearer access tokens.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@ConfigurationProperties(prefix = "spring.security.oauth2.resourceserver.jwt")
public class JwtAudienceProperties {
    private String audience;

    /**
     * Returns the configured resource audience.
     *
     * @return the expected audience, or null when none is configured
     */
    public String getAudience() {
        return audience;
    }

    /**
     * Sets the audience expected by the resource server.
     *
     * @param audience expected JWT audience
     */
    public void setAudience(String audience) {
        this.audience = audience;
    }

    /**
     * Converts the optional single audience property to the validator's list contract.
     *
     * @return an empty list when audience validation is not configured
     */
    public List<String> asList() {
        return (audience == null || audience.isBlank()) ? List.of() : List.of(audience);
    }
}
