package com.emerald.backend.auth;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Component;

import java.util.regex.Pattern;

/**
 * Creates the first admin account when (and only when) no admin exists yet.
 *
 * There are no built-in credentials: the email and password come from the
 * environment (EMERALD_AUTH_BOOTSTRAP_EMAIL / EMERALD_AUTH_BOOTSTRAP_PASSWORD),
 * the password is stored only as an Argon2id hash and is never logged. Remove
 * both variables after the first start. Without them, and with no admin in the
 * database, nobody can sign in (the safe failure mode).
 */
@Component
public class AdminBootstrap implements ApplicationRunner {

    private static final Logger log = LoggerFactory.getLogger(AdminBootstrap.class);
    private static final int MIN_PASSWORD_LENGTH = 14;
    private static final Pattern EMAIL = Pattern.compile("^[^@\\s]{1,64}@[^@\\s]{1,189}$");

    private final AdminUserRepository users;
    private final PasswordEncoder passwordEncoder;
    private final AuthProperties.Bootstrap bootstrap;

    public AdminBootstrap(AdminUserRepository users, PasswordEncoder passwordEncoder, AuthProperties properties) {
        this.users = users;
        this.passwordEncoder = passwordEncoder;
        this.bootstrap = properties.bootstrap();
    }

    @Override
    public void run(ApplicationArguments args) {
        if (users.count() > 0) {
            return;
        }
        String email = bootstrap.email();
        String password = bootstrap.password();
        if (email == null || email.isBlank() || password == null || password.isEmpty()) {
            log.warn("No admin account exists and no bootstrap credentials are configured. "
                    + "Set EMERALD_AUTH_BOOTSTRAP_EMAIL and EMERALD_AUTH_BOOTSTRAP_PASSWORD for the first start.");
            return;
        }

        String normalized = AuthService.normalizeEmail(email);
        if (!EMAIL.matcher(normalized).matches()) {
            throw new IllegalStateException("emerald.auth.bootstrap.email is not a valid email address");
        }
        if (password.length() < MIN_PASSWORD_LENGTH) {
            throw new IllegalStateException(
                    "emerald.auth.bootstrap.password must be at least " + MIN_PASSWORD_LENGTH + " characters");
        }

        users.save(new AdminUser(normalized, passwordEncoder.encode(password), AdminUser.ROLE_ADMIN));
        log.info("Initial admin account created. Remove the bootstrap credentials from the environment now.");
    }
}
