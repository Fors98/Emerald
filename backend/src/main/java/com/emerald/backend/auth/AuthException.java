package com.emerald.backend.auth;

/**
 * Authentication failures. Messages are generic on purpose and are what the
 * client sees (see AuthExceptionHandler). Stack traces are disabled: these are
 * expected control-flow outcomes, not bugs.
 */
public abstract class AuthException extends RuntimeException {

    protected AuthException(String message) {
        super(message, null, false, false);
    }

    /** Wrong email/password, unknown account, locked or disabled account. Always the same message. */
    public static final class InvalidCredentials extends AuthException {
        public InvalidCredentials() {
            super("Incorrect email or password.");
        }
    }

    /** Missing, unknown, expired, revoked or replayed refresh token. */
    public static final class InvalidRefreshToken extends AuthException {
        public InvalidRefreshToken() {
            super("Your session has expired. Please sign in again.");
        }
    }

    /** The access token is valid but the account it belongs to is gone or disabled. */
    public static final class Unauthenticated extends AuthException {
        public Unauthenticated() {
            super("Authentication required.");
        }
    }

    /** Too many failed attempts from one client address. */
    public static final class TooManyAttempts extends AuthException {
        private final long retryAfterSeconds;

        public TooManyAttempts(long retryAfterSeconds) {
            super("Too many failed attempts. Please try again later.");
            this.retryAfterSeconds = retryAfterSeconds;
        }

        public long getRetryAfterSeconds() {
            return retryAfterSeconds;
        }
    }

    /** Cookie-authenticated endpoint called from an origin that is not on the allow-list (CSRF defence). */
    public static final class UntrustedOrigin extends AuthException {
        public UntrustedOrigin() {
            super("Request origin is not allowed.");
        }
    }
}
