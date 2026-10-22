package com.emerald.backend.auth;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;
import java.util.UUID;

public interface AdminUserRepository extends JpaRepository<AdminUser, UUID> {

    Optional<AdminUser> findByEmail(String email);

    /**
     * Row-locks the account for the duration of the login transaction so that
     * parallel guesses cannot race past the failed-attempt counter.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select u from AdminUser u where u.email = :email")
    Optional<AdminUser> findByEmailForUpdate(@Param("email") String email);
}
