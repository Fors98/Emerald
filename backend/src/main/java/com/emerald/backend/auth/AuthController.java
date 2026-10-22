package com.emerald.backend.auth;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.*;

import java.util.Locale;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Admin authentication. Mounted under the context path, so the public URLs are
 * /api/auth/login, /api/auth/refresh, /api/auth/logout and /api/auth/me.
 *
 * The access token is returned in the JSON body (the admin page keeps it in
 * memory/sessionStorage and sends it as a Bearer header). The refresh token is
 * set ONLY as an HttpOnly cookie and never appears in a body.
 */
@RestController
@RequestMapping("/auth")
public class AuthController {

    private final AuthService authService;
    private final RefreshCookieFactory cookies;
    private final Set<String> trustedOrigins;

    public AuthController(AuthService authService,
                          RefreshCookieFactory cookies,
                          AuthProperties properties) {
        this.authService = authService;
        this.cookies = cookies;
        this.trustedOrigins = properties.allowedOrigins().stream()
                .map(AuthController::normalizeOrigin)
                .collect(Collectors.toUnmodifiableSet());
        if (trustedOrigins.isEmpty()) {
            throw new IllegalStateException(
                    "emerald.auth.allowed-origins must list the origin(s) of the admin page "
                            + "(e.g. EMERALD_AUTH_ALLOWED_ORIGINS=https://emerald.example)");
        }
    }

    /** POST /api/auth/login */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(@Valid @RequestBody AuthRequest request,
                                              HttpServletRequest http) {
        return withRefreshCookie(authService.login(request, clientIp(http)));
    }

    /** POST /api/auth/refresh - rotates the refresh cookie and returns a new access token. */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(HttpServletRequest http) {
        requireTrustedOrigin(http);
        return withRefreshCookie(authService.refresh(cookies.read(http), clientIp(http)));
    }

    /**
     * POST /api/auth/logout - ends the session identified by the refresh cookie.
     * Works even when the access token has already expired; always answers 204.
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(HttpServletRequest http) {
        requireTrustedOrigin(http);
        authService.logout(cookies.read(http));
        return ResponseEntity.noContent()
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .build();
    }

    /** GET /api/auth/me - requires a valid access token (enforced by the security filter chain). */
    @GetMapping("/me")
    public AuthResponse.User me(@AuthenticationPrincipal Jwt jwt) {
        return authService.currentUser(jwt);
    }

    // ---------------------------------------------------------------- helpers

    private ResponseEntity<AuthResponse> withRefreshCookie(AuthService.IssuedSession session) {
        return ResponseEntity.ok()
                .header(HttpHeaders.SET_COOKIE,
                        cookies.issue(session.refreshToken(), session.refreshMaxAge()).toString())
                .body(session.response());
    }

    /**
     * CSRF defence for the two cookie-authenticated endpoints: the browser
     * always sends Origin on cross-origin POSTs, and it must be on the
     * allow-list. (SameSite=Strict on the cookie is the first line of defence.)
     */
    private void requireTrustedOrigin(HttpServletRequest http) {
        String origin = http.getHeader(HttpHeaders.ORIGIN);
        if (origin == null || !trustedOrigins.contains(normalizeOrigin(origin))) {
            throw new AuthException.UntrustedOrigin();
        }
    }

    /**
     * The TCP peer address. X-Forwarded-For is deliberately NOT read here: it is
     * client-controlled unless a trusted proxy overwrites it. Behind a reverse
     * proxy, enable server.forward-headers-strategy so the container resolves
     * the real client address safely.
     */
    private static String clientIp(HttpServletRequest http) {
        String ip = http.getRemoteAddr();
        return ip == null ? "unknown" : ip;
    }

    private static String normalizeOrigin(String origin) {
        String value = origin.trim().toLowerCase(Locale.ROOT);
        return value.endsWith("/") ? value.substring(0, value.length() - 1) : value;
    }
}
