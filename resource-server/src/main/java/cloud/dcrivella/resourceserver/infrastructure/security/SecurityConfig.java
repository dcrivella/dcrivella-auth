package cloud.dcrivella.resourceserver.infrastructure.security;

import org.springframework.beans.factory.ObjectProvider;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.boot.security.oauth2.server.resource.autoconfigure.OAuth2ResourceServerProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jwt.*;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

import java.util.Objects;

/**
 * Configures bearer token authentication, issuer validation, audience validation and scope authorities.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@Configuration
@EnableMethodSecurity
@EnableWebSecurity
@EnableConfigurationProperties(JwtAudienceProperties.class)
public class SecurityConfig {

    /**
     * Requires authentication outside the public allowlist and enables JWT bearer authentication when a decoder exists.
     *
     * @param http shared Spring Security builder
     * @param jwtDecoderProvider optional decoder supplied when issuer and JWKS locations are configured
     * @return the resource server security filter chain
     */
    @Bean
    protected SecurityFilterChain securityFilterChain(HttpSecurity http, ObjectProvider<JwtDecoder> jwtDecoderProvider) {
        http //
                .authorizeHttpRequests(auth -> auth //
                        .requestMatchers("/actuator/**", "/error").permitAll() //
                        .anyRequest().authenticated());

        // Only configure JWT resource server when a JwtDecoder is available (e.g., issuer configured).
        if (jwtDecoderProvider.getIfAvailable() != null) {
            http.oauth2ResourceServer(oauth -> oauth.jwt(jwt -> jwt.jwtAuthenticationConverter(jwtAuthenticationConverter())));
        }
        return http.build();
    }

    /**
     * Creates a decoder that obtains signing keys from the configured JWKS URI and validates timestamps, issuer and audience.
     *
     * @param props Spring resource server JWT properties
     * @param audienceProps expected resource audience
     * @return the configured JWT decoder
     */
    @Bean
    @ConditionalOnProperty(prefix = "spring.security.oauth2.resourceserver.jwt", name = {"issuer-uri", "jwk-set-uri"})
    protected JwtDecoder jwtDecoder(OAuth2ResourceServerProperties props, JwtAudienceProperties audienceProps) {
        String issuer = props.getJwt().getIssuerUri();
        String jwkSetUri = props.getJwt().getJwkSetUri();
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withJwkSetUri(Objects.requireNonNull(jwkSetUri)).build();

        OAuth2TokenValidator<Jwt> withIssuer = JwtValidators.createDefaultWithIssuer(Objects.requireNonNull(issuer));
        OAuth2TokenValidator<Jwt> withAudience = new AudienceValidator(audienceProps.asList());
        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(withIssuer, withAudience));
        return decoder;
    }

    /**
     * Converts JWT scopes to {@code SCOPE_} authorities and uses {@code sub} as the authenticated principal.
     *
     * @return the JWT authentication converter
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
