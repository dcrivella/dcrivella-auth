package cloud.dcrivella.clientserver;

import java.io.IOException;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;
import org.springframework.security.web.authentication.AuthenticationFailureHandler;
import org.springframework.stereotype.Component;

/**
 * Redirects failed OpenID Connect logins to a safe public status page.
 *
 * @author Douglas Crivella
 * @created August 9, 2026
 */
@Component
public class OAuth2LoginFailureHandler implements AuthenticationFailureHandler {

    private static final String AUTHENTICATION_FAILED = "authentication_failed";

    /**
     * Preserves declined consent while reducing every other failure to a generic error code.
     *
     * @param request failed login request
     * @param response response receiving the safe redirect
     * @param exception authentication failure raised by Spring Security
     * @throws IOException when the response cannot send the redirect
     */
    @Override
    public void onAuthenticationFailure(HttpServletRequest request, HttpServletResponse response, AuthenticationException exception)
            throws IOException {
        String error = isAccessDenied(exception) ? OAuth2ErrorCodes.ACCESS_DENIED : AUTHENTICATION_FAILED;
        response.sendRedirect(request.getContextPath() + "/login?error=" + error);
    }

    /**
     * Checks whether the authorization server reported declined consent.
     *
     * @param exception authentication failure raised by Spring Security
     * @return {@code true} only for the standard OAuth {@code access_denied} error
     */
    private static boolean isAccessDenied(AuthenticationException exception) {
        return exception instanceof OAuth2AuthenticationException oauthException
                && OAuth2ErrorCodes.ACCESS_DENIED.equals(oauthException.getError().getErrorCode());
    }
}
