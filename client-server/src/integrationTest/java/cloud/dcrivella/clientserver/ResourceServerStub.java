package cloud.dcrivella.clientserver;

import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Runs a controllable resource server stub over a real local HTTP connection.
 *
 * <p>
 * This is not a mocked WebClient. The production {@link WebClientResourceServerClient} sends normal HTTP requests to an in-process JDK
 * {@link HttpServer} bound to a dynamic loopback port. Each test controls only the status and body returned by that server and can inspect
 * the captured Authorization header. This isolates the external resource server while still testing request creation, Bearer token
 * propagation and response handling through the real WebClient adapter.
 *
 * @author Douglas Crivella
 * @created August 9, 2026
 */
final class ResourceServerStub implements AutoCloseable {

    private static final StubResponse DEFAULT_RESPONSE = new StubResponse(500, "");

    private final AtomicReference<String> authorizationHeader = new AtomicReference<>();
    private final AtomicReference<StubResponse> response = new AtomicReference<>(DEFAULT_RESPONSE);
    private final HttpServer server;

    /**
     * Starts a local HTTP server on an available loopback port.
     *
     * @throws IOException when the server cannot bind to a local port
     */
    ResourceServerStub() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/tasks", this::handleTasks);
        server.start();
    }

    /**
     * Returns the dynamic base URL used to configure the production WebClient.
     *
     * @return local server base URL
     */
    String baseUrl() {
        return "http://127.0.0.1:" + server.getAddress().getPort();
    }

    /**
     * Selects the status and body returned by the next task request.
     *
     * @param status HTTP response status
     * @param body HTTP response body
     */
    void respondWith(int status, String body) {
        response.set(new StubResponse(status, body));
    }

    /**
     * Returns the Authorization header captured from the latest task request.
     *
     * @return captured header, or {@code null} before a request is received
     */
    String authorizationHeader() {
        return authorizationHeader.get();
    }

    /** Restores the default failure response and clears the captured Authorization header. */
    void reset() {
        response.set(DEFAULT_RESPONSE);
        authorizationHeader.set(null);
    }

    /** Stops the local HTTP server when the Spring test context closes. */
    @Override
    public void close() {
        server.stop(0);
    }

    /**
     * Captures the incoming Bearer header and writes the response selected by the test.
     *
     * @param exchange current task request and response exchange
     * @throws IOException when the response cannot be written
     */
    private void handleTasks(HttpExchange exchange) throws IOException {
        authorizationHeader.set(exchange.getRequestHeaders().getFirst("Authorization"));
        StubResponse currentResponse = response.get();
        byte[] body = currentResponse.body().getBytes(StandardCharsets.UTF_8);
        if (body.length == 0) {
            exchange.sendResponseHeaders(currentResponse.status(), -1);
            exchange.close();
            return;
        }

        exchange.sendResponseHeaders(currentResponse.status(), body.length);
        try (var output = exchange.getResponseBody()) {
            output.write(body);
        }
    }

    /**
     * Immutable HTTP response selected for the resource server stub.
     *
     * @param status HTTP response status
     * @param body HTTP response body
     */
    private record StubResponse(int status, String body) {
    }
}
