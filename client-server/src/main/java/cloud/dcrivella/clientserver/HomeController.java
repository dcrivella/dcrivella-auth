package cloud.dcrivella.clientserver;

import com.nimbusds.jwt.SignedJWT;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.ResponseBody;

/**
 * Renders the signed-in user's tokens, claims and protected task response.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
@Controller
public class HomeController {

    private final ResourceServerClient resourceServerClient;

    /**
     * Creates the controller with the port used to retrieve protected tasks.
     *
     * @param resourceServerClient protected task client port
     */
    public HomeController(ResourceServerClient resourceServerClient) {
        this.resourceServerClient = resourceServerClient;
    }

    /**
     * Redirects the public root path to the home page.
     *
     * @return the home page redirect view
     */
    @GetMapping("/")
    public String index() {
        return "redirect:/home";
    }

    /**
     * Populates the home page with the authorized client, OpenID Connect user and protected tasks.
     *
     * @param client authorized OAuth client, when login has completed
     * @param oidcUser authenticated OpenID Connect user
     * @param model MVC model rendered by the home template
     * @return the home template name
     */
    @GetMapping("/home")
    public String home(@RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient client, @AuthenticationPrincipal OidcUser oidcUser,
            org.springframework.ui.Model model) {
        OAuth2AccessToken at = client != null ? client.getAccessToken() : null;
        OAuth2RefreshToken rt = client != null ? client.getRefreshToken() : null;

        model.addAttribute("userName", oidcUser != null ? oidcUser.getFullName() : null);
        model.addAttribute("accessTokenValue", at != null ? at.getTokenValue() : null);
        model.addAttribute("refreshTokenValue", rt != null ? rt.getTokenValue() : null);
        model.addAttribute("idTokenValue", oidcUser != null ? oidcUser.getIdToken().getTokenValue() : null);

        // Pretty-print ID token claims and access token claims for visibility
        model.addAttribute("claimsJson", oidcUser != null ? oidcUser.getClaims().toString() : null);
        String atClaims = null;
        if (at != null) {
            try {
                var jwt = SignedJWT.parse(at.getTokenValue());
                atClaims = jwt.getJWTClaimsSet().getClaims().toString();
            } catch (Exception _) {
                atClaims = "<unable to parse access token claims>";
            }
        }
        model.addAttribute("accessTokenClaimsJson", atClaims);

        // Fetch tasks from resource-server
        model.addAttribute("tasksResponse", fetchTasks(at));

        return "home";
    }

    /**
     * Refreshes only the protected task fragment using the authorized client's server-side token.
     *
     * @param client authorized OAuth client, when login has completed
     * @return a non-cacheable task fragment or an authentication failure response
     */
    @GetMapping(value = "/home/tasks", produces = MediaType.TEXT_HTML_VALUE)
    @ResponseBody
    public ResponseEntity<String> refreshTasks(@RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient client) {
        OAuth2AccessToken accessToken = client != null ? client.getAccessToken() : null;
        if (accessToken == null) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_HTML)
                    .body("<p>Tasks could not be refreshed. Please sign in again.</p>");
        }

        return ResponseEntity.ok().cacheControl(CacheControl.noStore()).contentType(MediaType.TEXT_HTML).body(fetchTasks(accessToken));
    }

    /**
     * Retrieves protected tasks when an access token is available.
     *
     * @param accessToken access token held by the client server
     * @return the task fragment, or {@code null} when no token is available
     */
    private String fetchTasks(OAuth2AccessToken accessToken) {
        return accessToken != null ? resourceServerClient.fetchTasks(accessToken.getTokenValue()) : null;
    }
}
