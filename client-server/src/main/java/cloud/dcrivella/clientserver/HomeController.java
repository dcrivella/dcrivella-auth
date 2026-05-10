package cloud.dcrivella.clientserver;

import com.nimbusds.jwt.SignedJWT;
import org.springframework.http.HttpStatusCode;
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

    @GetMapping("/")
    public String index() {
        return "redirect:/home";
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
        String tasks = fetchTasks(at);
        model.addAttribute("tasksResponse", tasks);

        return "home";
    }

    private String fetchTasks(OAuth2AccessToken at) {
        if (at == null)
            return null;
        return this.resourceServerApi.get().uri("/tasks") //
                .headers(h -> h.setBearerAuth(at.getTokenValue())) //
                .exchangeToMono(resp -> { //
                    if (resp.statusCode().is2xxSuccessful()) {
                        return resp.bodyToMono(String.class);
                    }
                    return resp.bodyToMono(String.class) //
                            .defaultIfEmpty("") //
                            .map(body -> { //
                                HttpStatusCode sc = resp.statusCode();
                                String reason;
                                if (sc.isSameCodeAs(HttpStatusCode.valueOf(401))) {
                                    reason = "Unauthorized (likely missing or invalid audience)";
                                } else if (sc.isSameCodeAs(HttpStatusCode.valueOf(403))) {
                                    reason = "Forbidden (insufficient scope/authority)";
                                } else {
                                    reason = "Error";
                                }
                                String detail = body.isBlank() ? "" : ("\nDetails: " + body);
                                return "<pre>" + reason + " [" + sc.value() + "]" + detail + "</pre>";
                            });
                }) //
                .block();
    }
}
