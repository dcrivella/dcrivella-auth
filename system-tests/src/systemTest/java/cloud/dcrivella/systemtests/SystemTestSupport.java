package cloud.dcrivella.systemtests;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

/**
 * Shared black-box HTTP support for smoke and end-to-end tests against an active runtime.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
abstract class SystemTestSupport {

    protected static final HttpClient HTTP = HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(3))
            .followRedirects(HttpClient.Redirect.NEVER).build();
    protected static final String AUTH_SERVER_URL = property("systemTest.authServerUrl");
    protected static final String CLIENT_SERVER_URL = property("systemTest.clientServerUrl");
    protected static final String RESOURCE_SERVER_URL = property("systemTest.resourceServerUrl");
    protected static final String EXPECTED_ISSUER = property("systemTest.issuer");

    /**
     * Sends a GET request to a path on one application.
     *
     * @param baseUrl application base URL
     * @param path HTTP path
     * @return the black-box HTTP response
     * @throws Exception when the request cannot be completed
     */
    protected static HttpResponse<String> get(String baseUrl, String path) throws Exception {
        return send(HttpRequest.newBuilder(endpoint(baseUrl, path)).timeout(Duration.ofSeconds(5)).GET().build());
    }

    /**
     * Sends an HTTP request without following redirects.
     *
     * @param request request to send
     * @return the black-box HTTP response
     * @throws Exception when the request cannot be completed
     */
    protected static HttpResponse<String> send(HttpRequest request) throws Exception {
        return HTTP.send(request, HttpResponse.BodyHandlers.ofString());
    }

    /**
     * Resolves an application path against its externally reachable base URL.
     *
     * @param baseUrl application base URL
     * @param path HTTP path
     * @return the resolved endpoint
     */
    protected static URI endpoint(String baseUrl, String path) {
        return URI.create(baseUrl + path);
    }

    /**
     * Maps an issuer-advertised authorization server URI to the public URL used by the system test.
     *
     * @param advertisedUri URI returned by OpenID Connect discovery
     * @return the externally reachable endpoint preserving path and query
     */
    protected static URI publicEndpointFor(String advertisedUri) {
        URI uri = URI.create(advertisedUri);
        String path = uri.getRawPath() + (uri.getRawQuery() == null ? "" : "?" + uri.getRawQuery());
        return endpoint(AUTH_SERVER_URL, path);
    }

    /**
     * Reads and normalizes one required Gradle system-test property.
     *
     * @param name system property name
     * @return the configured value without a trailing slash
     * @throws IllegalStateException when the required property is absent or blank
     */
    private static String property(String name) {
        String value = System.getProperty(name);
        if (value == null || value.isBlank()) {
            throw new IllegalStateException("Missing Gradle system-test property: " + name);
        }
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
