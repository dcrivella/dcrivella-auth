package cloud.dcrivella.authserver;

import org.springframework.boot.web.servlet.ServletListenerRegistrationBean;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configurers.oauth2.server.authorization.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.core.session.SessionRegistry;
import org.springframework.security.core.session.SessionRegistryImpl;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.session.HttpSessionEventPublisher;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static org.springframework.security.config.Customizer.withDefaults;

/**
 * Defines security filter chains for authorization server endpoints and local user login.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@Configuration
public class SecurityFilterConfig {

    private static final String LOGIN_URL = "/login";

    /**
     * Tracks authenticated HTTP sessions across both security filter chains.
     *
     * @return the shared in-memory session registry
     */
    @Bean
    protected SessionRegistry sessionRegistry() {
        return new SessionRegistryImpl();
    }

    /**
     * Registers servlet session lifecycle events with Spring Security's session registry.
     *
     * @return the servlet listener registration for session creation and destruction events
     */
    @Bean
    protected ServletListenerRegistrationBean<HttpSessionEventPublisher> httpSessionEventPublisher() {
        return new ServletListenerRegistrationBean<>(new HttpSessionEventPublisher());
    }

    /**
     * Protects OAuth 2.0 and OpenID Connect endpoints and enables the authorization server protocol support.
     *
     * @param http shared Spring Security builder
     * @param sessionRegistry shared authenticated-session registry
     * @return the highest-priority authorization server filter chain
     */
    @Bean
    @Order(1)
    protected SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http, SessionRegistry sessionRegistry) {
        OAuth2AuthorizationServerConfigurer as = new OAuth2AuthorizationServerConfigurer();
        RequestMatcher endpointsMatcher = as.getEndpointsMatcher();

        http
                // Limit this chain to the AS endpoints (includes /oauth2/authorize, /oauth2/token, /.well-known, etc.)
                .securityMatcher(endpointsMatcher) //
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated()) //
                // CSRF not required on AS endpoints
                .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher)) //
                // Redirect to the login page when not authenticated from the AS
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint(LOGIN_URL)))
                .sessionManagement(session -> session.maximumSessions(1).expiredUrl(LOGIN_URL).sessionRegistry(sessionRegistry))
                // Apply the AS configurer and enable OIDC
                .with(as, config -> config.oidc(withDefaults()));

        // Protect resource endpoints with JWT
        http.oauth2ResourceServer(oauth -> oauth.jwt(withDefaults()));

        return http.build();
    }

    /**
     * Configures form login for application requests outside the authorization server endpoints.
     *
     * @param http shared Spring Security builder
     * @param sessionRegistry shared authenticated-session registry
     * @return the fallback application filter chain
     */
    @Bean
    @Order(2)
    protected SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http, SessionRegistry sessionRegistry) {
        http.authorizeHttpRequests(authorize -> authorize //
                .requestMatchers("/error", LOGIN_URL, "/default-ui.css", "/favicon.ico").permitAll() //
                .anyRequest().authenticated()) //
                .formLogin(withDefaults()) // Local login form for the AS
                .sessionManagement(session -> session.maximumSessions(1).expiredUrl(LOGIN_URL).sessionRegistry(sessionRegistry));
        return http.build();
    }
}
