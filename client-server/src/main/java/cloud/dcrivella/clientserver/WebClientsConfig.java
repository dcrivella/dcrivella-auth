package cloud.dcrivella.clientserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Creates HTTP clients used to call the protected resource server.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@Configuration
public class WebClientsConfig {

    /**
     * Provides a reusable WebClient builder.
     *
     * @return a new WebClient builder
     */
    @Bean
    public WebClient.Builder webClientBuilder() {
        return WebClient.builder();
    }

    /**
     * Creates the WebClient bound to the configured resource server URL.
     *
     * @param builder reusable WebClient builder
     * @param baseUrl resource server base URL
     * @return the resource server WebClient
     */
    @Bean
    protected WebClient resourceServerApi(WebClient.Builder builder, @Value("${api.resource-server.url}") String baseUrl) {
        return builder.clone().baseUrl(baseUrl).build();
    }
}
