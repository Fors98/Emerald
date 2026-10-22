package com.emerald.backend.auth;

import jakarta.validation.Valid;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.boot.context.properties.bind.DefaultValue;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;
import java.util.List;

/**
 * All authentication settings, bound from {@code emerald.auth.*}.
 *
 * Secrets are never given a default: the application refuses to start if
 * {@code emerald.auth.jwt.secret} is missing. Set it through the environment
 * (EMERALD_AUTH_JWT_SECRET), never in a committed file.
 */
@Validated
@ConfigurationProperties(prefix = "emerald.auth")
public record AuthProperties(
        @Valid @DefaultValue Jwt jwt,
        @Valid @DefaultValue Refresh refresh,
        @Valid @DefaultValue Lockout lockout,
        @Valid @DefaultValue Bootstrap bootstrap,
        /** Exact browser origins (scheme://host[:port]) allowed to call the cookie endpoints and CORS. */
        @DefaultValue List<String> allowedOrigins,
        /** Reject plain-HTTP requests (set false only for local development). */
        @DefaultValue("true") boolean requireHttps) {

    public record Jwt(
            /** Base64-encoded HMAC key, at least 32 bytes decoded. Generate: openssl rand -base64 48 */
            @NotBlank String secret,
            @NotBlank @DefaultValue("emerald-backend") String issuer,
            @NotBlank @DefaultValue("emerald-admin") String audience,
            @NotNull @DefaultValue("10m") Duration accessTtl) {
    }

    public record Refresh(
            /** A refresh token expires after this much inactivity. */
            @NotNull @DefaultValue("8h") Duration idleTtl,
            /** A session (token family) can never live longer than this, however often it is refreshed. */
            @NotNull @DefaultValue("7d") Duration absoluteTtl,
            @NotBlank @DefaultValue("emerald_rt") String cookieName,
            /** Must cover the /auth endpoints; includes the servlet context path. */
            @NotBlank @DefaultValue("/api/auth") String cookiePath,
            @DefaultValue("true") boolean cookieSecure) {
    }

    public record Lockout(
            /** Consecutive wrong passwords before an account is temporarily locked. */
            @Min(1) @DefaultValue("5") int maxFailedAttempts,
            @NotNull @DefaultValue("15m") Duration lockDuration,
            /** Failed attempts allowed per client address inside {@code ipWindow}. */
            @Min(1) @DefaultValue("10") int ipMaxFailures,
            @NotNull @DefaultValue("15m") Duration ipWindow) {
    }

    /** Optional: creates the first admin on startup when no admin exists. Remove after first start. */
    public record Bootstrap(String email, String password) {
    }
}
