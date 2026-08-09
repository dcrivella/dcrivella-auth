package cloud.dcrivella.clientserver;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.oauth2.core.OAuth2AuthenticationException;
import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2ErrorCodes;

/**
 * Tests safe redirects for declined consent and other login failures.
 *
 * @author Douglas Crivella
 * @created August 9, 2026
 */
class OAuth2LoginFailureHandlerTest {

    private final OAuth2LoginFailureHandler handler = new OAuth2LoginFailureHandler();

    /**
     * Verifies that the standard declined-consent error reaches the dedicated page state.
     *
     * @throws IOException when the mock response cannot record the redirect
     */
    @Test
    void preservesAccessDeniedForDeclinedConsent() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        MockHttpServletResponse response = new MockHttpServletResponse();
        OAuth2Error error = new OAuth2Error(OAuth2ErrorCodes.ACCESS_DENIED);

        handler.onAuthenticationFailure(request, response, new OAuth2AuthenticationException(error));

        assertThat(response.getRedirectedUrl()).isEqualTo("/login?error=access_denied");
    }

    /**
     * Verifies that non-OAuth failures do not expose their details in the redirect.
     *
     * @throws IOException when the mock response cannot record the redirect
     */
    @Test
    void reducesOtherFailuresToAGenericErrorCode() throws IOException {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.setContextPath("/client");
        MockHttpServletResponse response = new MockHttpServletResponse();

        handler.onAuthenticationFailure(request, response, new BadCredentialsException("sensitive provider detail"));

        assertThat(response.getRedirectedUrl()).isEqualTo("/client/login?error=authentication_failed");
        assertThat(response.getRedirectedUrl()).doesNotContain("sensitive provider detail");
    }
}
