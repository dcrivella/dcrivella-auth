package cloud.dcrivella.clientserver;

import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

/**
 * Renders a safe public page when an OpenID Connect sign-in is not completed.
 *
 * @author Douglas Crivella
 * @created August 9, 2026
 */
@Controller
public class LoginController {

    /**
     * Distinguishes declined consent from other login failures without exposing provider details.
     *
     * @param error OAuth error code returned by the authorization server, when present
     * @param model MVC model rendered by the login template
     * @return the public login status template name
     */
    @GetMapping("/login")
    public String login(@RequestParam(name = "error", required = false) String error, Model model) {
        model.addAttribute("consentDenied", OAuth2ErrorCodes.ACCESS_DENIED.equals(error));
        model.addAttribute("loginFailed", error != null);
        return "login";
    }
}
