package cloud.dcrivella.resourceserver.security;

import org.springframework.boot.autoconfigure.security.oauth2.resource.OAuth2ResourceServerProperties;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtDecoders;
import org.springframework.security.oauth2.jwt.JwtValidators;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Resource Server security configuration.
 * <p>
 * Responsibilities:
 * <ul>
 *   <li>Enables method security so annotations like {@code @PreAuthorize} are enforced.</li>
 *   <li>Secures all endpoints (except a small allowlist) using JWT Bearer tokens.</li>
 *   <li>Configures a {@code JwtDecoder} that validates the token issuer and, if configured,
 *       enforces that the token's {@code aud} claim contains at least one expected audience.</li>
 *   <li>Maps {@code scope}/{@code scp} claims to Spring authorities using the {@code SCOPE_} prefix,
 *       and sets the JWT {@code sub} as the authentication principal.</li>
 * </ul>
 */
@Configuration
@EnableMethodSecurity
@EnableConfigurationProperties(JwtAudienceProperties.class)
public class SecurityConfig {

    /**
     * Configures HTTP security to require authentication for all requests except
     * a small allowlist and to use JWT Bearer token authentication.
     */
    @Bean
    protected SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http //
                .authorizeHttpRequests(auth -> auth //
                        .requestMatchers("/actuator/**", "/error").permitAll() //
                        .anyRequest().authenticated()) //
                .oauth2ResourceServer(oauth -> oauth //
                        .jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        return http.build();
    }

    /**
     * Creates a {@link JwtDecoder} that validates the issuer and, optionally, the audience.
     * Audience value is read from the property
     * {@code spring.security.oauth2.resourceserver.jwt.audience}. If not set,
     * audience validation is skipped.
     */
    @Bean
    protected JwtDecoder jwtDecoder(OAuth2ResourceServerProperties props, JwtAudienceProperties audienceProps) {
        String issuer = props.getJwt().getIssuerUri();
        JwtDecoder decoder = JwtDecoders.fromIssuerLocation(issuer);

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(issuer);
        OAuth2TokenValidator<Jwt> withAudience = new AudienceValidator(audienceProps.asList());
        ((NimbusJwtDecoder) decoder).setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }

    /**
     * Converts JWT {@code scope}/{@code scp} claims into authorities prefixed with {@code SCOPE_}
     * and uses the {@code sub} claim as the principal.
     */
    private static JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter scopes = new JwtGrantedAuthoritiesConverter();
        // Default claim names are "scope" and "scp"; default prefix is "SCOPE_".
        scopes.setAuthorityPrefix("SCOPE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(scopes);
        converter.setPrincipalClaimName("sub");
        return converter;
    }
}
