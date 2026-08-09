package cloud.dcrivella.clientserver;

import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.reactive.function.client.WebClient;

/**
 * WebClient adapter that calls the protected task endpoint with a bearer token.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@Component
public class WebClientResourceServerClient implements ResourceServerClient {

    private final WebClient resourceServerApi;

    /**
     * Creates the adapter with the WebClient configured for the resource server.
     *
     * @param resourceServerApi resource server WebClient
     */
    public WebClientResourceServerClient(@Qualifier("resourceServerApi") WebClient resourceServerApi) {
        this.resourceServerApi = resourceServerApi;
    }

    /**
     * Retrieves tasks and preserves the client server's existing HTML response contract.
     *
     * @param accessToken bearer token issued for the resource server
     * @return the task response or a user-facing HTTP error representation
     */
    @Override
    public String fetchTasks(String accessToken) {
        return resourceServerApi.get().uri("/tasks") //
                .headers(headers -> headers.setBearerAuth(accessToken)) //
                .exchangeToMono(response -> { //
                    if (response.statusCode().is2xxSuccessful()) {
                        return response.bodyToMono(String.class);
                    }
                    return response.bodyToMono(String.class) //
                            .defaultIfEmpty("") //
                            .map(body -> errorResponse(response.statusCode(), body));
                }) //
                .block();
    }

    /**
     * Converts an unsuccessful resource server response to the existing HTML error representation.
     *
     * @param status resource server HTTP status
     * @param body optional response details
     * @return an HTML-safe preformatted error message
     */
    private static String errorResponse(HttpStatusCode status, String body) {
        String reason;
        if (status.isSameCodeAs(HttpStatusCode.valueOf(401))) {
            reason = "Unauthorized (likely missing or invalid audience)";
        } else if (status.isSameCodeAs(HttpStatusCode.valueOf(403))) {
            reason = "Forbidden (grant api.read on the consent screen and sign in again)";
        } else {
            reason = "Error";
        }
        String detail = body.isBlank() ? "" : ("\nDetails: " + body);
        return "<pre>" + reason + " [" + status.value() + "]" + detail + "</pre>";
    }
}
