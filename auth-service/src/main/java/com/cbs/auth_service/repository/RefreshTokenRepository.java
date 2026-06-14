package com.cbs.auth_service.repository;

import java.time.Instant;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cbs.auth_service.entity.RefreshToken;

@Repository
public interface RefreshTokenRepository extends JpaRepository<RefreshToken, UUID> {

    Optional<RefreshToken> findByTokenHash(String tokenHash);

    List<RefreshToken> findAllByUserIdAndRevokedFalse(UUID userId);

    /** Revoke all active tokens for a user — called on logout and reuse detection. */
    @Modifying
    @Query("""
        UPDATE RefreshToken rt
           SET rt.revoked = TRUE,
               rt.revokedAt = CURRENT_TIMESTAMP
         WHERE rt.user.id = :userId
           AND rt.revoked = FALSE
        """)
    int revokeAllForUser(@Param("userId") UUID userId);

    /** Nightly cleanup: delete expired or revoked tokens older than 30 days. */
    @Modifying
    @Query("""
        DELETE FROM RefreshToken rt
         WHERE rt.revoked = TRUE
            OR rt.expiresAt < :cutoff
        """)
    int deleteExpiredAndRevoked(@Param("cutoff") Instant cutoff);
}
