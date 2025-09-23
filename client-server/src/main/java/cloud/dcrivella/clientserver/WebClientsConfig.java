package cloud.dcrivella.clientserver;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.reactive.function.client.WebClient;

@Configuration
public class WebClientsConfig {

    @Bean
    protected WebClient resourceServerApi(WebClient.Builder builder,
            @Value("${api.resource-server.url}") String baseUrl) {
        return builder.clone().baseUrl(baseUrl).build();
    }
}
