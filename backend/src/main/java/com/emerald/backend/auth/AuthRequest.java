package com.emerald.backend.auth;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

/**
 * Login payload. Same shape as api.yaml {@code LoginRequest}.
 *
 * Upper bounds are deliberate: they stop oversized inputs from reaching the
 * (intentionally expensive) password hash. There is no minimum length on the
 * password here, because login must never tell an attacker what the password
 * policy is. Deliberately has no toString(), so the password cannot be logged
 * by accident.
 */
public class AuthRequest {

    @NotBlank
    @Email
    @Size(max = 254)
    private String email;

    @NotBlank
    @Size(max = 128)
    private String password;

    public String getEmail() {
        return email;
    }

    public void setEmail(String email) {
        this.email = email;
    }

    public String getPassword() {
        return password;
    }

    public void setPassword(String password) {
        this.password = password;
    }
}
