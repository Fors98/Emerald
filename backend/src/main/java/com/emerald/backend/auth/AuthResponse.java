package com.emerald.backend.auth;

/**
 * Body returned by POST /auth/login and POST /auth/refresh.
 * Same shape as api.yaml {@code LoginResponse}.
 *
 * Contains only what the client needs: the short-lived access token, its
 * lifetime and the minimal user identity. The refresh token is NEVER part of
 * the body; it travels only in an HttpOnly cookie.
 */
public class AuthResponse {

    private final String accessToken;
    private final long expiresIn;
    private final User user;

    public AuthResponse(String accessToken, long expiresIn, User user) {
        this.accessToken = accessToken;
        this.expiresIn = expiresIn;
        this.user = user;
    }

    public String getAccessToken() {
        return accessToken;
    }

    public String getTokenType() {
        return "Bearer";
    }

    /** Access-token lifetime in seconds. */
    public long getExpiresIn() {
        return expiresIn;
    }

    public User getUser() {
        return user;
    }

    /** Same shape as api.yaml {@code User}; also the body of GET /auth/me. */
    public static class User {

        private final String email;
        private final String role;

        public User(String email, String role) {
            this.email = email;
            this.role = role;
        }

        public String getEmail() {
            return email;
        }

        public String getRole() {
            return role;
        }
    }
}
