package com.emerald.backend.auth;

import jakarta.servlet.http.Cookie;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.http.ResponseCookie;
import org.springframework.stereotype.Component;
import org.springframework.web.util.WebUtils;

import java.time.Duration;

/**
 * Builds and reads the refresh-token cookie.
 *
 * HttpOnly (JavaScript can never read it), Secure (HTTPS only, configurable for
 * local development), SameSite=Strict (never sent on cross-site requests) and
 * scoped to the /auth path so it is not attached to any other endpoint.
 */
@Component
public class RefreshCookieFactory {

    private final AuthProperties.Refresh settings;

    public RefreshCookieFactory(AuthProperties properties) {
        this.settings = properties.refresh();
    }

    public ResponseCookie issue(String refreshToken, Duration maxAge) {
        return base(refreshToken).maxAge(maxAge).build();
    }

    /** A cookie that tells the browser to delete the refresh token. */
    public ResponseCookie clear() {
        return base("").maxAge(Duration.ZERO).build();
    }

    /** The raw refresh token sent by the browser, or null. */
    public String read(HttpServletRequest request) {
        Cookie cookie = WebUtils.getCookie(request, settings.cookieName());
        return cookie == null ? null : cookie.getValue();
    }

    private ResponseCookie.ResponseCookieBuilder base(String value) {
        return ResponseCookie.from(settings.cookieName(), value)
                .httpOnly(true)
                .secure(settings.cookieSecure())
                .sameSite("Strict")
                .path(settings.cookiePath());
    }
}
