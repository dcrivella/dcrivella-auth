package cloud.dcrivella.resourceserver;

import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Primary;
import org.springframework.security.oauth2.jwt.JwtDecoder;

/**
 * Test override to avoid network calls for OIDC discovery/JWKs. Provides a no-op JwtDecoder so the application context can load.
 */
@TestConfiguration
public class TestSecurityConfig {

    @Bean
    @Primary
    protected JwtDecoder testJwtDecoder() {
        return (String _) -> {
            throw new UnsupportedOperationException("Test JwtDecoder: decoding not supported in context load tests");
        };
    }
}
