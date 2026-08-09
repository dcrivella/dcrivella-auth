package cloud.dcrivella.authserver;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.core.userdetails.User;
import org.springframework.security.core.userdetails.UserDetailsService;
import org.springframework.security.provisioning.InMemoryUserDetailsManager;

/**
 * Provides the in-memory resource owner used by the authorization code examples.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@Configuration
public class UserStoreConfig {

    /**
     * Creates the demonstration user store used by local login.
     *
     * @return an in-memory user details service containing the configured resource owner
     */
    @Bean
    protected UserDetailsService userDetailsService() {
        var userDetailsManager = new InMemoryUserDetailsManager();
        userDetailsManager.createUser(User.withUsername("user").password("{noop}pass").roles("USER").build());
        userDetailsManager.createUser(User.withUsername("playwright").password("{noop}playwright-pass").roles("USER").build());
        return userDetailsManager;
    }
}
