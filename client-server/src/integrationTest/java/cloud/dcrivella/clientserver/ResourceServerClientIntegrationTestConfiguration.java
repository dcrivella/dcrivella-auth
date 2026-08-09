package cloud.dcrivella.clientserver;

import java.io.IOException;
import org.springframework.boot.test.context.TestConfiguration;
import org.springframework.context.annotation.Bean;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * Connects the production resource server client adapter to a controlled local HTTP server.
 *
 * <p>
 * The stub owns the external HTTP boundary and records requests while each test selects the response it should return. This lets the
 * integration suite verify the real WebClient request, Bearer token propagation and response handling without depending on a running
 * resource server.
 *
 * @author Douglas Crivella
 * @created August 9, 2026
 */
@TestConfiguration(proxyBeanMethods = false)
class ResourceServerClientIntegrationTestConfiguration {

    /**
     * Starts the controllable resource server stub and closes it with the Spring test context.
     *
     * @return local HTTP stub used as the resource server boundary
     * @throws IOException when the local HTTP server cannot bind to a dynamic port
     */
    @Bean(destroyMethod = "close")
    ResourceServerStub resourceServerStub() throws IOException {
        return new ResourceServerStub();
    }

    /**
     * Creates the production adapter with a WebClient directed to the local resource server stub.
     *
     * @param resourceServerStub controlled HTTP boundary that receives adapter requests
     * @return production resource server client connected to the stub
     */
    @Bean
    ResourceServerClient resourceServerClient(ResourceServerStub resourceServerStub) {
        WebClient webClient = WebClient.builder().baseUrl(resourceServerStub.baseUrl()).build();
        return new WebClientResourceServerClient(webClient);
    }
}
