package com.emerald.backend.auth;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    /**
     * Atomic compare-and-set: succeeds (returns 1) for exactly one caller even
     * if the same token is presented concurrently. A return value of 0 means the
     * token was already used, revoked or expired.
     */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.usedAt = :now "
            + "where t.id = :id and t.usedAt is null and t.revokedAt is null and t.expiresAt > :now")
    int markUsed(@Param("id") UUID id, @Param("now") Instant now);

    /** Revokes every token of a session; also invalidates its access tokens. */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now "
            + "where t.familyId = :familyId and t.revokedAt is null")
    int revokeFamily(@Param("familyId") UUID familyId, @Param("now") Instant now);

    /** Revokes every session of one user (e.g. when the account is disabled). */
    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("update RefreshToken t set t.revokedAt = :now "
            + "where t.userId = :userId and t.revokedAt is null")
    int revokeAllForUser(@Param("userId") UUID userId, @Param("now") Instant now);

    /**
     * A session is live while its single not-yet-used, not-revoked, unexpired
     * token exists. Used to validate the "sid" claim on every access token.
     */
    @Query("select count(t) > 0 from RefreshToken t "
            + "where t.familyId = :familyId and t.usedAt is null and t.revokedAt is null "
            + "and t.expiresAt > :now and t.sessionExpiresAt > :now")
    boolean hasLiveSession(@Param("familyId") UUID familyId, @Param("now") Instant now);

    @Modifying
    @Query("delete from RefreshToken t where t.sessionExpiresAt < :cutoff")
    int deleteSessionsEndedBefore(@Param("cutoff") Instant cutoff);
}
