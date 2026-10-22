package com.emerald.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.core.Ordered;
import org.springframework.core.annotation.Order;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Error handling for AuthController only (assignableTypes), so the existing
 * ContactValidationHandler keeps serving the rest of the API unchanged.
 *
 * Follows the project conventions from api.yaml: validation failures are
 * {@code 400 { "errors": { field: message } }}, everything else is
 * {@code { "message": "..." }}. Messages are fixed strings: no exception text,
 * stack trace, token or credential ever reaches the client.
 */
@RestControllerAdvice(assignableTypes = AuthController.class)
@Order(Ordered.HIGHEST_PRECEDENCE)
public class AuthExceptionHandler {

    private static final Logger log = LoggerFactory.getLogger(AuthExceptionHandler.class);

    private final RefreshCookieFactory cookies;

    public AuthExceptionHandler(RefreshCookieFactory cookies) {
        this.cookies = cookies;
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<Map<String, Object>> handleValidation(MethodArgumentNotValidException exception) {
        Map<String, String> errors = new LinkedHashMap<>();
        exception.getBindingResult().getFieldErrors().forEach(error ->
                errors.putIfAbsent(error.getField(), error.getDefaultMessage()));
        return ResponseEntity.badRequest().body(Map.of("errors", errors));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<Map<String, Object>> handleUnreadable(HttpMessageNotReadableException exception) {
        return ResponseEntity.badRequest().body(message("Malformed request body."));
    }

    /** Same status and body for unknown email, wrong password, locked and disabled accounts. */
    @ExceptionHandler(AuthException.InvalidCredentials.class)
    public ResponseEntity<Map<String, Object>> handleInvalidCredentials(AuthException.InvalidCredentials exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message(exception.getMessage()));
    }

    /** Also tells the browser to drop the (now useless) refresh cookie. */
    @ExceptionHandler(AuthException.InvalidRefreshToken.class)
    public ResponseEntity<Map<String, Object>> handleInvalidRefresh(AuthException.InvalidRefreshToken exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED)
                .header(HttpHeaders.SET_COOKIE, cookies.clear().toString())
                .body(message(exception.getMessage()));
    }

    @ExceptionHandler(AuthException.Unauthenticated.class)
    public ResponseEntity<Map<String, Object>> handleUnauthenticated(AuthException.Unauthenticated exception) {
        return ResponseEntity.status(HttpStatus.UNAUTHORIZED).body(message(exception.getMessage()));
    }

    @ExceptionHandler(AuthException.TooManyAttempts.class)
    public ResponseEntity<Map<String, Object>> handleTooManyAttempts(AuthException.TooManyAttempts exception) {
        return ResponseEntity.status(HttpStatus.TOO_MANY_REQUESTS)
                .header(HttpHeaders.RETRY_AFTER, String.valueOf(exception.getRetryAfterSeconds()))
                .body(message(exception.getMessage()));
    }

    @ExceptionHandler(AuthException.UntrustedOrigin.class)
    public ResponseEntity<Map<String, Object>> handleUntrustedOrigin(AuthException.UntrustedOrigin exception) {
        return ResponseEntity.status(HttpStatus.FORBIDDEN).body(message(exception.getMessage()));
    }

    /** Safety net: log server-side, answer with nothing but a fixed message. */
    @ExceptionHandler(Exception.class)
    public ResponseEntity<Map<String, Object>> handleUnexpected(Exception exception) {
        log.error("Unexpected error in authentication endpoint", exception);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(message("Something went wrong. Please try again later."));
    }

    private static Map<String, Object> message(String text) {
        return Map.of("message", text);
    }
}
