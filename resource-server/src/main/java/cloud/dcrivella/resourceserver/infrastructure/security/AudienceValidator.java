package cloud.dcrivella.resourceserver.infrastructure.security;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.util.Collection;
import java.util.List;
import java.util.Objects;

/**
 * Validates that an access token targets at least one configured resource audience.
 *
 * @param requiredAudiences acceptable values for the JWT {@code aud} claim
 * @author Douglas Crivella
 * @created August 8, 2026
 */
public record AudienceValidator(List<String> requiredAudiences) implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error MISSING_AUDIENCE = new OAuth2Error("invalid_token", "The required audience is missing",
            "https://datatracker.ietf.org/doc/html/rfc6750#section-3.1");

    /** Normalizes a null audience configuration to an immutable empty list, which disables audience enforcement. */
    public AudienceValidator {
        requiredAudiences = requiredAudiences == null ? List.of() : List.copyOf(requiredAudiences);
    }

    /**
     * Validates the token audience against the configured resource audiences.
     *
     * @param jwt access token to validate
     * @return success when validation is disabled or an audience matches; otherwise an invalid-token failure
     */
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
