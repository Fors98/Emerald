package com.emerald.backend.auth;

import org.springframework.security.oauth2.core.OAuth2Error;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidatorResult;
import org.springframework.security.oauth2.jwt.Jwt;

import java.time.Instant;
import java.util.UUID;

/**
 * Rejects an otherwise valid access token when its session has been ended
 * (logout, refresh-token replay detection, expiry of the session, disabled
 * account). This is what makes access tokens revocable despite being JWTs.
 */
public class ActiveSessionValidator implements OAuth2TokenValidator<Jwt> {

    private static final OAuth2Error INVALID = new OAuth2Error("invalid_token", "Session is not active.", null);

    private final RefreshTokenRepository tokens;

    public ActiveSessionValidator(RefreshTokenRepository tokens) {
        this.tokens = tokens;
    }

    @Override
    public OAuth2TokenValidatorResult validate(Jwt jwt) {
        String sid = jwt.getClaimAsString(JwtService.CLAIM_SESSION);
        if (sid == null) {
            return OAuth2TokenValidatorResult.failure(INVALID);
        }
        UUID sessionId;
        try {
            sessionId = UUID.fromString(sid);
        } catch (IllegalArgumentException e) {
            return OAuth2TokenValidatorResult.failure(INVALID);
        }
        return tokens.hasLiveSession(sessionId, Instant.now())
                ? OAuth2TokenValidatorResult.success()
                : OAuth2TokenValidatorResult.failure(INVALID);
    }
}
