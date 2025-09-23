package cloud.dcrivella.clientserver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.oauth2.client.oidc.web.logout.OidcClientInitiatedLogoutSuccessHandler;
import org.springframework.security.oauth2.client.registration.ClientRegistrationRepository;
import org.springframework.security.web.SecurityFilterChain;

@Configuration
public class ClientSecurityConfig {

    @Bean
    protected SecurityFilterChain client(HttpSecurity http, ClientRegistrationRepository clients) throws Exception {
        // Redirect to AS end_session_endpoint with id_token_hint
        var oidcLogout = new OidcClientInitiatedLogoutSuccessHandler(clients);
        oidcLogout.setPostLogoutRedirectUri("{baseUrl}/"); // or /home

        http.authorizeHttpRequests(a -> a //
                        .requestMatchers("/", "/default-ui.css", "/favicon.ico", "/login**").permitAll() //
                        .anyRequest().authenticated()) //
                .oauth2Login(o -> o.defaultSuccessUrl("/home", true)) //
                .logout(l -> l.logoutSuccessHandler(oidcLogout));

        return http.build();
    }
}
