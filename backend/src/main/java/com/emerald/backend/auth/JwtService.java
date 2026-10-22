package com.emerald.backend.auth;

import com.nimbusds.jose.jwk.source.ImmutableSecret;
import org.springframework.security.oauth2.core.DelegatingOAuth2TokenValidator;
import org.springframework.security.oauth2.core.OAuth2TokenValidator;
import org.springframework.security.oauth2.jose.jws.MacAlgorithm;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.security.oauth2.jwt.JwtClaimNames;
import org.springframework.security.oauth2.jwt.JwtClaimValidator;
import org.springframework.security.oauth2.jwt.JwtClaimsSet;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.JwtEncoderParameters;
import org.springframework.security.oauth2.jwt.JwtIssuerValidator;
import org.springframework.security.oauth2.jwt.JwtTimestampValidator;
import org.springframework.security.oauth2.jwt.JwsHeader;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;
import org.springframework.stereotype.Service;

import javax.crypto.SecretKey;
import javax.crypto.spec.SecretKeySpec;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.Collection;
import java.util.List;
import java.util.UUID;

/**
 * Creates and validates access tokens.
 *
 * Tokens are HS256-signed JWTs. The algorithm is pinned: the decoder only
 * accepts HS256 and therefore rejects "none" and every other algorithm.
 * HS256 is appropriate because one service both issues and verifies the tokens;
 * switch to an asymmetric algorithm if another service ever has to verify them.
 *
 * Claims: iss, aud, sub (admin user id), iat, nbf, exp, jti, roles, sid
 * (session id = refresh-token family) and token_use=access.
 */
@Service
public class JwtService {

    public static final String CLAIM_ROLES = "roles";
    public static final String CLAIM_SESSION = "sid";
    public static final String CLAIM_TOKEN_USE = "token_use";
    public static final String TOKEN_USE_ACCESS = "access";

    private static final int MIN_SECRET_BYTES = 32;
    private static final Duration CLOCK_SKEW = Duration.ofSeconds(30);

    private final AuthProperties.Jwt settings;
    private final SecretKey key;
    private final JwtEncoder encoder;

    public JwtService(AuthProperties properties) {
        this.settings = properties.jwt();
        this.key = parseKey(settings.secret());
        this.encoder = new NimbusJwtEncoder(new ImmutableSecret<>(key));
    }

    /** A signed access token together with its lifetime. */
    public record AccessToken(String value, long expiresInSeconds) {
    }

    public AccessToken issue(AdminUser user, UUID sessionId) {
        Instant now = Instant.now();
        Duration ttl = settings.accessTtl();

        JwtClaimsSet claims = JwtClaimsSet.builder()
                .issuer(settings.issuer())
                .audience(List.of(settings.audience()))
                .subject(user.getId().toString())
                .issuedAt(now)
                .notBefore(now)
                .expiresAt(now.plus(ttl))
                .id(UUID.randomUUID().toString())
                .claim(CLAIM_ROLES, List.of(user.getRole()))
                .claim(CLAIM_SESSION, sessionId.toString())
                .claim(CLAIM_TOKEN_USE, TOKEN_USE_ACCESS)
                .build();

        JwsHeader header = JwsHeader.with(MacAlgorithm.HS256).type("JWT").build();
        String token = encoder.encode(JwtEncoderParameters.from(header, claims)).getTokenValue();
        return new AccessToken(token, ttl.toSeconds());
    }

    /**
     * Builds the decoder used by Spring Security on every protected request.
     * Verifies: signature + algorithm (HS256 only), exp/nbf (30 s skew), issuer,
     * audience, token type, presence of sub/jti/sid, and finally that the
     * session behind the token has not been revoked.
     */
    public JwtDecoder decoder(OAuth2TokenValidator<Jwt> sessionValidator) {
        NimbusJwtDecoder decoder = NimbusJwtDecoder.withSecretKey(key)
                .macAlgorithm(MacAlgorithm.HS256)
                .build();

        OAuth2TokenValidator<Jwt> audience = new JwtClaimValidator<Collection<String>>(
                JwtClaimNames.AUD,
                aud -> aud != null && aud.contains(settings.audience()));
        OAuth2TokenValidator<Jwt> tokenUse = new JwtClaimValidator<String>(
                CLAIM_TOKEN_USE, TOKEN_USE_ACCESS::equals);
        OAuth2TokenValidator<Jwt> hasSubject = new JwtClaimValidator<String>(
                JwtClaimNames.SUB, sub -> sub != null && !sub.isBlank());
        OAuth2TokenValidator<Jwt> hasJti = new JwtClaimValidator<String>(
                JwtClaimNames.JTI, jti -> jti != null && !jti.isBlank());
        OAuth2TokenValidator<Jwt> hasExpiry = new JwtClaimValidator<Instant>(
                JwtClaimNames.EXP, exp -> exp != null);

        decoder.setJwtValidator(new DelegatingOAuth2TokenValidator<>(
                new JwtTimestampValidator(CLOCK_SKEW),
                new JwtIssuerValidator(settings.issuer()),
                audience,
                tokenUse,
                hasSubject,
                hasJti,
                hasExpiry,
                sessionValidator));
        return decoder;
    }

    private static SecretKey parseKey(String base64Secret) {
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64Secret.trim());
        } catch (IllegalArgumentException e) {
            throw new IllegalStateException(
                    "emerald.auth.jwt.secret must be Base64-encoded (generate one with: openssl rand -base64 48)");
        }
        if (bytes.length < MIN_SECRET_BYTES) {
            throw new IllegalStateException(
                    "emerald.auth.jwt.secret must decode to at least " + MIN_SECRET_BYTES
                            + " bytes (generate one with: openssl rand -base64 48)");
        }
        return new SecretKeySpec(bytes, "HmacSHA256");
    }
}
