package com.cbs.auth_service.repository;


import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cbs.auth_service.entity.AuthUser;

@Repository
public interface AuthUserRepository extends JpaRepository<AuthUser, UUID> {

    Optional<AuthUser> findByUsername(String username);

    Optional<AuthUser> findByEmail(String email);

    boolean existsByUsername(String username);

    boolean existsByEmail(String email);

    Optional<AuthUser> findByCustomerId(UUID customerId);

    /** Reset lockout state after successful login — avoids full entity load for that path. */
    @Modifying
    @Query("""
        UPDATE AuthUser u
           SET u.failedLoginAttempts = 0,
               u.lockedUntil = NULL,
               u.lastLoginAt = CURRENT_TIMESTAMP
         WHERE u.id = :id
        """)
    void resetLockout(@Param("id") UUID id);
}