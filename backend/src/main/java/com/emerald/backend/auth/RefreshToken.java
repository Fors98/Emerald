package com.emerald.backend.auth;

import jakarta.persistence.*;
import java.time.Instant;
import java.util.UUID;

/**
 * One issued refresh token. Only the SHA-256 hash of the token is stored, so a
 * database leak does not yield usable tokens.
 *
 * Tokens form a "family" (= one login session). Each refresh marks the presented
 * token as used and issues a successor in the same family. Presenting a token
 * that is already used means it was stolen or replayed, so the whole family is
 * revoked. The family id is also carried in the access token ("sid") so that
 * revoking the family kills outstanding access tokens immediately.
 */
@Entity
@Table(name = "refresh_tokens", indexes = {
        @Index(name = "idx_refresh_tokens_family", columnList = "family_id"),
        @Index(name = "idx_refresh_tokens_session_expiry", columnList = "session_expires_at")
})
public class RefreshToken {

    @Id
    @GeneratedValue
    private UUID id;

    /** Lower-case hex SHA-256 of the raw token. */
    @Column(name = "token_hash", nullable = false, unique = true, length = 64)
    private String tokenHash;

    @Column(name = "family_id", nullable = false)
    private UUID familyId;

    @Column(name = "user_id", nullable = false)
    private UUID userId;

    @Column(name = "issued_at", nullable = false)
    private Instant issuedAt;

    /** Idle expiry of this individual token (never later than sessionExpiresAt). */
    @Column(name = "expires_at", nullable = false)
    private Instant expiresAt;

    /** Absolute end of the whole session; identical for every token in the family. */
    @Column(name = "session_expires_at", nullable = false)
    private Instant sessionExpiresAt;

    /** Set when this token has been exchanged for a successor. */
    @Column(name = "used_at")
    private Instant usedAt;

    /** Set when the family is revoked (logout, replay detection, disabled account). */
    @Column(name = "revoked_at")
    private Instant revokedAt;

    public RefreshToken() {
    }

    public RefreshToken(String tokenHash, UUID familyId, UUID userId,
                        Instant issuedAt, Instant expiresAt, Instant sessionExpiresAt) {
        this.tokenHash = tokenHash;
        this.familyId = familyId;
        this.userId = userId;
        this.issuedAt = issuedAt;
        this.expiresAt = expiresAt;
        this.sessionExpiresAt = sessionExpiresAt;
    }

    public UUID getId() {
        return id;
    }

    public String getTokenHash() {
        return tokenHash;
    }

    public UUID getFamilyId() {
        return familyId;
    }

    public UUID getUserId() {
        return userId;
    }

    public Instant getIssuedAt() {
        return issuedAt;
    }

    public Instant getExpiresAt() {
        return expiresAt;
    }

    public Instant getSessionExpiresAt() {
        return sessionExpiresAt;
    }

    public Instant getUsedAt() {
        return usedAt;
    }

    public Instant getRevokedAt() {
        return revokedAt;
    }
}
