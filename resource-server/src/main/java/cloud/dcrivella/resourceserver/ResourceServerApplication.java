package cloud.dcrivella.resourceserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * Starts the resource server that exposes bearer-token-protected tasks.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@SpringBootApplication
public class ResourceServerApplication {

    /**
     * Starts the resource server application.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    static void main(String[] args) {
        SpringApplication.run(ResourceServerApplication.class, args);
    }
}
