package cloud.dcrivella.authserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.oauth2.server.authorization.settings.AuthorizationServerSettings;

@Configuration
public class AuthorizationServerConfig {

    @Bean
    protected AuthorizationServerSettings authorizationServerSettings(@Value("${ISSUER_URL:}") String issuer) {
        AuthorizationServerSettings.Builder builder = AuthorizationServerSettings.builder();
        if (issuer != null && !issuer.isBlank()) {
            builder.issuer(issuer);
        }
        return builder.build();
    }
}
