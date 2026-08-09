package cloud.dcrivella.clientserver;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ImportRuntimeHints;

/**
 * Starts the OAuth client that signs users in and displays tasks from the resource server.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@SpringBootApplication
@ImportRuntimeHints(ClientServerRuntimeHints.class)
public class ClientServerApplication {

    /**
     * Starts the OAuth client application.
     *
     * @param args command-line arguments passed to Spring Boot
     */
    static void main(String[] args) {
        SpringApplication.run(ClientServerApplication.class, args);
    }
}
