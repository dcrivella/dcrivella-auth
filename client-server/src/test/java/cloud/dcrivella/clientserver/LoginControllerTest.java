package cloud.dcrivella.clientserver;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;
import org.springframework.ui.ExtendedModelMap;

/**
 * Tests the public login status page without starting a Spring context.
 *
 * @author Douglas Crivella
 * @created August 9, 2026
 */
class LoginControllerTest {

    private final LoginController controller = new LoginController();

    /** Verifies that declined consent receives the dedicated safe page state. */
    @Test
    void rendersConsentDeniedState() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.login("access_denied", model);

        assertThat(view).isEqualTo("login");
        assertThat(model).containsEntry("consentDenied", true).containsEntry("loginFailed", true);
    }

    /** Verifies that another provider error receives a generic failure state. */
    @Test
    void rendersGenericLoginFailureState() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.login("temporarily_unavailable", model);

        assertThat(view).isEqualTo("login");
        assertThat(model).containsEntry("consentDenied", false).containsEntry("loginFailed", true);
    }

    /** Verifies that opening the login entry point without an error renders its neutral state. */
    @Test
    void rendersNeutralLoginState() {
        ExtendedModelMap model = new ExtendedModelMap();

        String view = controller.login(null, model);

        assertThat(view).isEqualTo("login");
        assertThat(model).containsEntry("consentDenied", false).containsEntry("loginFailed", false);
    }
}
