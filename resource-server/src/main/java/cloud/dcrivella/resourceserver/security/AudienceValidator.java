package cloud.dcrivella.resourceserver.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * OAuth2TokenValidator that enforces the presence of at least one expected audience in a JWT's {@code aud} claim.
 * <p>
 * In OAuth 2.0, the {@code aud} claim in an access token tells which resource server(s) the token is intended for.
 * <p>
 * Behavior: - If the configured audience list is empty, validation succeeds (no audience enforcement). - Otherwise, validation succeeds
 * when the token's {@code aud} contains any of the configured values.
 * <p>
 * This validator is typically composed with the default issuer and timestamp validators.
 */
public record AudienceValidator(List<String> requiredAudiences) implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error MISSING_AUDIENCE = new OAuth2Error("invalid_token", "The required audience is missing",
            "https://datatracker.ietf.org/doc/html/rfc6750#section-3.1");

    /**
     * Creates a validator with the provided list of acceptable audience values. A null list is treated as empty (disabling enforcement).
     */
    public AudienceValidator {
        requiredAudiences = requiredAudiences == null ? List.of() : List.copyOf(requiredAudiences);
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        if (requiredAudiences.isEmpty()) {
            return OAuth2TokenValidatorResult.success();
        }

        Collection<String> tokenAud = Objects.requireNonNullElse(jwt.getAudience(), List.of());
        boolean match = tokenAud.stream().anyMatch(requiredAudiences::contains);
        return match ? OAuth2TokenValidatorResult.success() : OAuth2TokenValidatorResult.failure(MISSING_AUDIENCE);
    }
}
