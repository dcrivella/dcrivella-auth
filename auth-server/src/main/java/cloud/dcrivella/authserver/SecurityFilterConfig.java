package cloud.dcrivella.authserver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.annotation.Order;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.server.authorization.config.annotation.web.configurers.OAuth2AuthorizationServerConfigurer;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.security.web.authentication.LoginUrlAuthenticationEntryPoint;
import org.springframework.security.web.util.matcher.RequestMatcher;

import static org.springframework.security.config.Customizer.withDefaults;

@Configuration
public class SecurityFilterConfig {

    @Bean
    @Order(1)
    protected SecurityFilterChain authorizationServerSecurityFilterChain(HttpSecurity http) throws Exception {
        OAuth2AuthorizationServerConfigurer as = OAuth2AuthorizationServerConfigurer.authorizationServer();
        RequestMatcher endpointsMatcher = as.getEndpointsMatcher();

        http
                // Limit this chain to the AS endpoints (includes /oauth2/authorize, /oauth2/token, /.well-known, etc.)
                .securityMatcher(endpointsMatcher) //
                .authorizeHttpRequests(auth -> auth.anyRequest().authenticated()) //
                // CSRF not required on AS endpoints
                .csrf(csrf -> csrf.ignoringRequestMatchers(endpointsMatcher)) //
                // Redirect to the login page when not authenticated from the AS
                .exceptionHandling(ex -> ex.authenticationEntryPoint(new LoginUrlAuthenticationEntryPoint("/login")))
                // Apply the AS configurer and enable OIDC
                .with(as, config -> config.oidc(withDefaults()));

        // Protect resource endpoints with JWT
        http.oauth2ResourceServer(oauth -> oauth.jwt(withDefaults()));

        return http.build();
    }

    @Bean
    @Order(2)
    protected SecurityFilterChain defaultSecurityFilterChain(HttpSecurity http) throws Exception {
        http.authorizeHttpRequests(authorize -> authorize //
                        .requestMatchers("/error", "/login", "/default-ui.css", "/favicon.ico").permitAll() //
                        .anyRequest().authenticated()) //
                .formLogin(withDefaults()); // Local login form for the AS
        return http.build();
    }
}
