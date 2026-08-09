package cloud.dcrivella.clientserver;

/**
 * Port used by the client server to retrieve protected tasks.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
public interface ResourceServerClient {

    /**
     * Retrieves the task representation using an OAuth access token.
     *
     * @param accessToken bearer token issued for the resource server
     * @return the task response or the existing user-facing error representation
     */
    String fetchTasks(String accessToken);
}
