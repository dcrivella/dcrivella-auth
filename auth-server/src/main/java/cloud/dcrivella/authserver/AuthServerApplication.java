package cloud.dcrivella.authserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the authorization server that authenticates users and issues OAuth 2.0 and OpenID Connect tokens.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@SpringBootApplication
public class AuthServerApplication {

    /**
     * Starts the authorization server application.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    static void main(String[] args) {
        SpringApplication.run(AuthServerApplication.class, args);
    }
}
