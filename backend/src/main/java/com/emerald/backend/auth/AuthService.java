package com.emerald.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.TimeUnit;

/**
 * Credential verification and token lifecycle.
 *
 * Lifecycle: login creates a session (a refresh-token "family"). Every refresh
 * rotates the refresh token. Reuse of an already-rotated token revokes the whole
 * session. Logout revokes the session. Access tokens carry the session id and are
 * rejected as soon as the session is revoked (see ActiveSessionValidator).
 *
 * Failure paths are @Transactional(noRollbackFor = AuthException.class) on
 * purpose: the lockout counter and the replay-revocation must be COMMITTED even
 * though the method then throws.
 */
@Service
public class AuthService {

    private static final Logger log = LoggerFactory.getLogger(AuthService.class);
    private static final SecureRandom RANDOM = new SecureRandom();
    private static final int REFRESH_TOKEN_BYTES = 32; // 256 bits
    private static final int MAX_REFRESH_TOKEN_CHARS = 128;
    /**
     * Argon2id deliberately needs ~32 MiB per verification. Cap how many run at once so a burst of
     * parallel login requests cannot exhaust the heap before the per-IP failure limiter reacts.
     */
    private static final int MAX_CONCURRENT_LOGINS = 4;
    private static final long LOGIN_QUEUE_WAIT_MILLIS = 2_000;

    private final AdminUserRepository users;
    private final RefreshTokenRepository tokens;
    private final PasswordEncoder passwordEncoder;
    private final JwtService jwtService;
    private final AuthProperties properties;
    private final AttemptLimiter loginLimiter;
    private final AttemptLimiter refreshLimiter;
    private final Semaphore loginSlots = new Semaphore(MAX_CONCURRENT_LOGINS);

    /** Hash of a random password nobody knows; verified against when the account does not exist. */
    private final String dummyHash;

    public AuthService(AdminUserRepository users,
                       RefreshTokenRepository tokens,
                       PasswordEncoder passwordEncoder,
                       JwtService jwtService,
                       AuthProperties properties) {
        this.users = users;
        this.tokens = tokens;
        this.passwordEncoder = passwordEncoder;
        this.jwtService = jwtService;
        this.properties = properties;
        this.loginLimiter = new AttemptLimiter(
                properties.lockout().ipMaxFailures(), properties.lockout().ipWindow());
        this.refreshLimiter = new AttemptLimiter(
                properties.lockout().ipMaxFailures() * 2, properties.lockout().ipWindow());
        this.dummyHash = passwordEncoder.encode(randomToken());
    }

    /** Everything the controller needs to answer a successful login/refresh. */
    public record IssuedSession(AuthResponse response, String refreshToken, Duration refreshMaxAge) {
    }

    // ------------------------------------------------------------------ login

    @Transactional(noRollbackFor = AuthException.class)
    public IssuedSession login(AuthRequest request, String clientIp) {
        loginLimiter.assertAllowed(clientIp);

        boolean acquired = false;
        try {
            acquired = loginSlots.tryAcquire(LOGIN_QUEUE_WAIT_MILLIS, TimeUnit.MILLISECONDS);
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        if (!acquired) {
            throw new AuthException.TooManyAttempts(5);
        }
        try {
            return attemptLogin(request, clientIp);
        } finally {
            loginSlots.release();
        }
    }

    private IssuedSession attemptLogin(AuthRequest request, String clientIp) {
        Instant now = Instant.now();
        String email = normalizeEmail(request.getEmail());

        AdminUser user = users.findByEmailForUpdate(email).orElse(null);
        boolean usable = user != null && user.isEnabled() && !user.isLockedAt(now);

        // Always run exactly one Argon2 verification, whether or not the account
        // exists or is locked, so response time does not reveal which case it was.
        String hash = usable ? user.getPasswordHash() : dummyHash;
        boolean passwordMatches = passwordEncoder.matches(request.getPassword(), hash);

        if (!usable || !passwordMatches) {
            loginLimiter.recordFailure(clientIp);
            if (usable) {
                registerFailedAttempt(user, now);
            }
            // No email, no password in the log: both are attacker-controlled input.
            log.warn("Login failed from {}", clientIp);
            throw new AuthException.InvalidCredentials();
        }

        user.setFailedAttempts(0);
        user.setLockedUntil(null);
        user.setLastLoginAt(now);
        if (passwordEncoder.upgradeEncoding(user.getPasswordHash())) {
            user.setPasswordHash(passwordEncoder.encode(request.getPassword()));
        }

        UUID sessionId = UUID.randomUUID();
        Instant sessionExpiresAt = now.plus(properties.refresh().absoluteTtl());
        log.info("Login succeeded for user {}", user.getId());
        return issue(user, sessionId, sessionExpiresAt, now);
    }

    private void registerFailedAttempt(AdminUser user, Instant now) {
        int failed = user.getFailedAttempts() + 1;
        if (failed >= properties.lockout().maxFailedAttempts()) {
            user.setLockedUntil(now.plus(properties.lockout().lockDuration()));
            user.setFailedAttempts(0);
            log.warn("Account {} temporarily locked after repeated failed logins", user.getId());
        } else {
            user.setFailedAttempts(failed);
        }
    }

    // ---------------------------------------------------------------- refresh

    @Transactional(noRollbackFor = AuthException.class)
    public IssuedSession refresh(String rawToken, String clientIp) {
        refreshLimiter.assertAllowed(clientIp);

        if (rawToken == null || rawToken.isBlank() || rawToken.length() > MAX_REFRESH_TOKEN_CHARS) {
            refreshLimiter.recordFailure(clientIp);
            throw new AuthException.InvalidRefreshToken();
        }

        Instant now = Instant.now();
        RefreshToken token = tokens.findByTokenHash(sha256Hex(rawToken)).orElse(null);
        if (token == null || token.getRevokedAt() != null) {
            refreshLimiter.recordFailure(clientIp);
            throw new AuthException.InvalidRefreshToken();
        }

        if (token.getUsedAt() != null) {
            // An already-rotated token is being presented again: it was stolen or replayed.
            // End the whole session so neither party keeps access.
            tokens.revokeFamily(token.getFamilyId(), now);
            refreshLimiter.recordFailure(clientIp);
            log.warn("Refresh-token replay detected; session {} of user {} revoked",
                    token.getFamilyId(), token.getUserId());
            throw new AuthException.InvalidRefreshToken();
        }

        if (!token.getExpiresAt().isAfter(now) || !token.getSessionExpiresAt().isAfter(now)) {
            refreshLimiter.recordFailure(clientIp);
            throw new AuthException.InvalidRefreshToken();
        }

        AdminUser user = users.findById(token.getUserId()).orElse(null);
        if (user == null || !user.isEnabled()) {
            tokens.revokeFamily(token.getFamilyId(), now);
            throw new AuthException.InvalidRefreshToken();
        }

        // Atomic: only one concurrent caller can consume a given token.
        if (tokens.markUsed(token.getId(), now) != 1) {
            tokens.revokeFamily(token.getFamilyId(), now);
            log.warn("Concurrent refresh-token use detected; session {} of user {} revoked",
                    token.getFamilyId(), token.getUserId());
            throw new AuthException.InvalidRefreshToken();
        }

        return issue(user, token.getFamilyId(), token.getSessionExpiresAt(), now);
    }

    // ----------------------------------------------------------------- logout

    /** Idempotent: unknown or missing tokens are ignored. */
    @Transactional
    public void logout(String rawToken) {
        if (rawToken == null || rawToken.isBlank() || rawToken.length() > MAX_REFRESH_TOKEN_CHARS) {
            return;
        }
        tokens.findByTokenHash(sha256Hex(rawToken)).ifPresent(token -> {
            tokens.revokeFamily(token.getFamilyId(), Instant.now());
            log.info("Session {} of user {} ended by logout", token.getFamilyId(), token.getUserId());
        });
    }

    // --------------------------------------------------------------------- me

    @Transactional(readOnly = true)
    public AuthResponse.User currentUser(Jwt jwt) {
        UUID id;
        try {
            id = UUID.fromString(jwt.getSubject());
        } catch (IllegalArgumentException | NullPointerException e) {
            throw new AuthException.Unauthenticated();
        }
        AdminUser user = users.findById(id).filter(AdminUser::isEnabled)
                .orElseThrow(AuthException.Unauthenticated::new);
        return new AuthResponse.User(user.getEmail(), user.getRole());
    }

    // ---------------------------------------------------------------- helpers

    /** Stores a new refresh token in the session and signs a matching access token. */
    private IssuedSession issue(AdminUser user, UUID sessionId, Instant sessionExpiresAt, Instant now) {
        String rawRefresh = randomToken();
        Instant idleExpiry = now.plus(properties.refresh().idleTtl());
        Instant refreshExpiresAt = idleExpiry.isBefore(sessionExpiresAt) ? idleExpiry : sessionExpiresAt;

        tokens.save(new RefreshToken(sha256Hex(rawRefresh), sessionId, user.getId(),
                now, refreshExpiresAt, sessionExpiresAt));

        JwtService.AccessToken access = jwtService.issue(user, sessionId);
        AuthResponse body = new AuthResponse(access.value(), access.expiresInSeconds(),
                new AuthResponse.User(user.getEmail(), user.getRole()));
        return new IssuedSession(body, rawRefresh, Duration.between(now, refreshExpiresAt));
    }

    static String normalizeEmail(String email) {
        return email == null ? "" : email.trim().toLowerCase(Locale.ROOT);
    }

    private static String randomToken() {
        byte[] bytes = new byte[REFRESH_TOKEN_BYTES];
        RANDOM.nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Refresh tokens carry 256 bits of entropy, so a plain (unsalted) SHA-256 is the right primitive. */
    private static String sha256Hex(String value) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            return HexFormat.of().formatHex(digest.digest(value.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 is not available", e);
        }
    }
}
