package cloud.dcrivella.resourceserver.infrastructure.security;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.List;
import org.junit.jupiter.api.Test;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

/**
 * Tests resource audience validation independently from JWT decoding and issuer validation.
 *
 * @author Douglas Crivella
 * @created August 8, 2026
 */
class AudienceValidatorTest {

    /** Verifies that an absent audience configuration disables audience enforcement. */
    @Test
    void acceptsAnyAudienceWhenNoAudienceIsConfigured() {
        assertThat(new AudienceValidator(null).validate(jwt(List.of("another-api"))).hasErrors()).isFalse();
        assertThat(new AudienceValidator(List.of()).validate(jwt(List.of())).hasErrors()).isFalse();
    }

    /** Verifies that one matching value is sufficient when several resource audiences are accepted. */
    @Test
    void acceptsWhenAnyConfiguredAudienceMatches() {
        OAuth2TokenValidatorResult result = new AudienceValidator(List.of("api://first", "api://resource-server"))
                .validate(jwt(List.of("api://resource-server", "api://another")));

        assertThat(result.hasErrors()).isFalse();
    }

    /** Verifies that a token without an {@code aud} claim is rejected when an audience is required. */
    @Test
    void rejectsATokenWithoutAnAudience() {
        OAuth2TokenValidatorResult result = new AudienceValidator(List.of("api://resource-server")).validate(jwtWithoutAudience());

        assertMissingAudience(result);
    }

    /** Verifies that a token containing only unexpected audiences is rejected. */
    @Test
    void rejectsATokenWithOnlyUnexpectedAudiences() {
        OAuth2TokenValidatorResult result = new AudienceValidator(List.of("api://resource-server")).validate(jwt(List.of("api://another")));

        assertMissingAudience(result);
    }

    /**
     * Asserts the RFC 6750 error returned for a missing resource audience.
     *
     * @param result audience validation result
     */
    private static void assertMissingAudience(OAuth2TokenValidatorResult result) {
        assertThat(result.hasErrors()).isTrue();
        assertThat(result.getErrors()).singleElement().satisfies(error -> {
            assertThat(error.getErrorCode()).isEqualTo("invalid_token");
            assertThat(error.getDescription()).isEqualTo("The required audience is missing");
            assertThat(error.getUri()).isEqualTo("https://datatracker.ietf.org/doc/html/rfc6750#section-3.1");
        });
    }

    /**
     * Builds a JWT fixture with the supplied audiences.
     *
     * @param audience values stored in the {@code aud} claim
     * @return the JWT fixture
     */
    private static Jwt jwt(List<String> audience) {
        return Jwt.withTokenValue("token").header("alg", "none").audience(audience).build();
    }

    /**
     * Builds a JWT fixture without an {@code aud} claim.
     *
     * @return the JWT fixture
     */
    private static Jwt jwtWithoutAudience() {
        return Jwt.withTokenValue("token").header("alg", "none").subject("subject-123").build();
    }
}
