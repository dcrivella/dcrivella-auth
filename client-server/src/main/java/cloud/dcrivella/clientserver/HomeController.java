package cloud.dcrivella.clientserver;

import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.client.OAuth2AuthorizedClient;
import org.springframework.security.oauth2.client.annotation.RegisteredOAuth2AuthorizedClient;
import org.springframework.security.oauth2.core.OAuth2AccessToken;
import org.springframework.security.oauth2.core.OAuth2RefreshToken;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.reactive.function.client.WebClient;

@Controller
public class HomeController {

    private final WebClient resourceServerApi;

    public HomeController(WebClient resourceServerApi) {
        this.resourceServerApi = resourceServerApi;
    }

    @GetMapping("/home")
    public String home(@RegisteredOAuth2AuthorizedClient OAuth2AuthorizedClient client,
            @AuthenticationPrincipal OidcUser oidcUser, org.springframework.ui.Model model) {
        OAuth2AccessToken at = client != null ? client.getAccessToken() : null;
        OAuth2RefreshToken rt = client != null ? client.getRefreshToken() : null;

        model.addAttribute("userName", oidcUser != null ? oidcUser.getFullName() : null);
        model.addAttribute("accessTokenValue", at != null ? at.getTokenValue() : null);
        model.addAttribute("refreshTokenValue", rt != null ? rt.getTokenValue() : null);
        model.addAttribute("idTokenValue", oidcUser != null ? oidcUser.getIdToken().getTokenValue() : null);

        // Pretty-print claims as a single String to avoid reflection in the view
        model.addAttribute("claimsJson", oidcUser != null ? oidcUser.getClaims().toString() : null);

        // Fetch tasks from resource-server
        String tasksHtml = null;
        if (at != null) {
            tasksHtml = this.resourceServerApi //
                    .get() //
                    .uri("/tasks") //
                    .headers(h -> h.setBearerAuth(at.getTokenValue())) //
                    .retrieve() //
                    .bodyToMono(String.class) //
                    .block();
        }
        model.addAttribute("tasksHtml", tasksHtml);

        return "home";
    }
}
