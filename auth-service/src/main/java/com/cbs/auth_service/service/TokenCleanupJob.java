package com.cbs.auth_service.service;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cbs.auth_service.repository.RefreshTokenRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Nightly maintenance job: removes expired and revoked refresh tokens from PostgreSQL.
 *
 * <p>Redis entries expire automatically via TTL. This job only cleans the DB table.
 * Runs at 02:00 UTC daily to avoid peak-hour load.
 *
 * <p>The {@code cutoff} is set to 7 days in the past — any token that expired or
 * was revoked more than 7 days ago is safe to delete from the audit table.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenCleanupJob {

    private static final int RETENTION_DAYS = 7;

    private final RefreshTokenRepository refreshTokenRepository;

    @Scheduled(cron = "0 0 2 * * *", zone = "UTC")   // 02:00 UTC daily
    @Transactional
    public void purgeExpiredTokens() {
        Instant cutoff = Instant.now().minus(RETENTION_DAYS, ChronoUnit.DAYS);
        int deleted = refreshTokenRepository.deleteExpiredAndRevoked(cutoff);
        log.info("TokenCleanupJob: deleted {} expired/revoked refresh token record(s)", deleted);
    }
}