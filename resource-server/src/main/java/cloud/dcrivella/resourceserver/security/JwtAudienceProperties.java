package cloud.dcrivella.resourceserver.security;

import org.springframework.boot.context.properties.ConfigurationProperties;

import java.util.List;

/**
 * Binds audience enforcement settings from {@code spring.security.oauth2.resourceserver.jwt.audience}.
 * <p>
 * This project standardizes on a single audience per resource server.
 */
@ConfigurationProperties(prefix = "spring.security.oauth2.resourceserver.jwt")
public class JwtAudienceProperties {
    private String audience;

    public String getAudience() {
        return audience;
    }

    public void setAudience(String audience) {
        this.audience = audience;
    }

    public List<String> asList() {
        return (audience == null || audience.isBlank()) ? List.of() : List.of(audience);
    }
}
