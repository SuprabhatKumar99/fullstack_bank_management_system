package com.cbs.auth_service.service;


import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.Base64;
import java.util.HexFormat;
import java.util.concurrent.TimeUnit;

import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cbs.auth_service.config.JwtProperties;
import com.cbs.auth_service.dto.response.AuthEvent;
import com.cbs.auth_service.dto.response.AuthResponse;
import com.cbs.auth_service.entity.AuthUser;
import com.cbs.auth_service.entity.RefreshToken;
import com.cbs.auth_service.exception.InvalidTokenException;
import com.cbs.auth_service.exception.TokenReuseException;
import com.cbs.auth_service.repository.AuthUserRepository;
import com.cbs.auth_service.repository.RefreshTokenRepository;
import com.cbs.auth_service.security.JwtService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Manages the full lifecycle of refresh tokens.
 *
 * <h3>Storage strategy</h3>
 * <ul>
 *   <li><strong>Redis</strong> – fast lookup by raw token hash; TTL mirrors token expiry.</li>
 *   <li><strong>PostgreSQL</strong> – source of truth for revocation, audit, and reuse detection.</li>
 * </ul>
 *
 * <h3>Token rotation</h3>
 * Each call to {@link #rotateRefreshToken} revokes the incoming token and issues a new one.
 * If a previously revoked token is presented again, <em>all</em> tokens for that user
 * are invalidated immediately (token family rotation / reuse detection).
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class TokenService {

    private static final String REDIS_PREFIX = "auth:refresh:";

    private final JwtService              jwtService;
    private final JwtProperties           jwtProperties;
    private final RefreshTokenRepository  refreshTokenRepository;
    private final AuthUserRepository      authUserRepository;
    private final RedisTemplate<String, String> redisTemplate;
    private final AuthEventPublisher      eventPublisher;

    // ── Issue ─────────────────────────────────────────────────

    /**
     * Issues a new access + refresh token pair.
     * Called by {@link AuthService} after successful login or registration.
     */
    @Transactional
    public AuthResponse issueTokenPair(AuthUser user, HttpServletRequest httpRequest) {
        String accessToken  = jwtService.generateAccessToken(user);
        String rawRefresh   = generateRawRefreshToken();
        String refreshHash  = sha256(rawRefresh);

        Instant now    = Instant.now();
        Instant expiry = now.plusMillis(jwtProperties.getRefreshTokenExpiryMs());

        RefreshToken entity = RefreshToken.builder()
            .tokenHash(refreshHash)
            .user(user)
            .issuedAt(now)
            .expiresAt(expiry)
            .ipAddress(extractIp(httpRequest))
            .userAgent(extractUserAgent(httpRequest))
            .build();

        refreshTokenRepository.save(entity);
        cacheInRedis(refreshHash, user.getId().toString(), expiry);

        return buildResponse(accessToken, rawRefresh, user);
    }

    // ── Rotate ────────────────────────────────────────────────

    /**
     * Validates the incoming refresh token, revokes it, and issues a new pair.
     *
     * <p>Reuse detection: if the presented token is already revoked, we treat it as a
     * potential token theft — all active tokens for that user are immediately revoked.
     */
    @Transactional
    public AuthResponse rotateRefreshToken(String rawRefreshToken, HttpServletRequest httpRequest) {
        String hash = sha256(rawRefreshToken);

        RefreshToken existing = refreshTokenRepository.findByTokenHash(hash)
            .orElseThrow(() -> new InvalidTokenException("Refresh token not found or already used"));

        // ── Reuse detection ──────────────────────────────────
        if (existing.isRevoked()) {
            log.warn("SECURITY: Refresh token reuse detected for userId={}", existing.getUser().getId());
            refreshTokenRepository.revokeAllForUser(existing.getUser().getId());
            evictAllFromRedis(existing.getUser().getId().toString());

            eventPublisher.publish(AuthEvent.builder()
                .eventType(AuthEvent.EventType.TOKEN_REUSE_DETECTED)
                .userId(existing.getUser().getId().toString())
                .username(existing.getUser().getUsername())
                .ipAddress(extractIp(httpRequest))
                .occurredAt(Instant.now())
                .build());

            throw new TokenReuseException("Token reuse detected. All sessions have been invalidated.");
        }

        if (existing.isExpired()) {
            throw new InvalidTokenException("Refresh token has expired. Please log in again.");
        }

        // ── Revoke old token ─────────────────────────────────
        existing.revoke();
        refreshTokenRepository.save(existing);
        evictFromRedis(hash);

        // ── Issue new pair ───────────────────────────────────
        AuthUser user = existing.getUser();
        String newAccessToken = jwtService.generateAccessToken(user);
        String rawRefresh     = generateRawRefreshToken();
        String newHash        = sha256(rawRefresh);

        Instant now    = Instant.now();
        Instant expiry = now.plusMillis(jwtProperties.getRefreshTokenExpiryMs());

        RefreshToken newToken = RefreshToken.builder()
            .tokenHash(newHash)
            .user(user)
            .issuedAt(now)
            .expiresAt(expiry)
            .ipAddress(extractIp(httpRequest))
            .userAgent(extractUserAgent(httpRequest))
            .build();

        refreshTokenRepository.save(newToken);
        cacheInRedis(newHash, user.getId().toString(), expiry);

        eventPublisher.publish(AuthEvent.builder()
            .eventType(AuthEvent.EventType.TOKEN_REFRESHED)
            .userId(user.getId().toString())
            .username(user.getUsername())
            .ipAddress(extractIp(httpRequest))
            .occurredAt(Instant.now())
            .build());

        return buildResponse(newAccessToken, rawRefresh, user);
    }

    // ── Revoke (logout) ───────────────────────────────────────

    /**
     * Revokes all active refresh tokens for the given user.
     * Called by logout and by admin token-invalidation endpoints.
     */
    @Transactional
    public void revokeAllTokensForUser(String userId, HttpServletRequest httpRequest) {
        java.util.UUID uid = java.util.UUID.fromString(userId);
        int revoked = refreshTokenRepository.revokeAllForUser(uid);
        evictAllFromRedis(userId);

        AuthUser user = authUserRepository.findById(uid).orElseThrow();

        eventPublisher.publish(AuthEvent.builder()
            .eventType(AuthEvent.EventType.LOGOUT)
            .userId(userId)
            .username(user.getUsername())
            .ipAddress(extractIp(httpRequest))
            .occurredAt(Instant.now())
            .detail("Revoked " + revoked + " token(s)")
            .build());

        log.info("Logout: userId={}, tokensRevoked={}", userId, revoked);
    }

    // ── Redis helpers ─────────────────────────────────────────

    private void cacheInRedis(String hash, String userId, Instant expiry) {
        long ttlSeconds = ChronoUnit.SECONDS.between(Instant.now(), expiry);
        redisTemplate.opsForValue().set(
            REDIS_PREFIX + hash, userId, ttlSeconds, TimeUnit.SECONDS);
    }

    private void evictFromRedis(String hash) {
        redisTemplate.delete(REDIS_PREFIX + hash);
    }

    /**
     * Evicts all Redis keys for a user by pattern scan.
     * Used on logout and reuse detection where individual hashes are not known.
     *
     * <p>Note: SCAN is used instead of KEYS to avoid blocking in production Redis clusters.
     */
    private void evictAllFromRedis(String userId) {
        // We store hash→userId, so we scan for values matching userId.
        // In a high-volume system, a reverse index (userId → Set<hash>) in Redis would be faster.
        // For the CBS portfolio scope, scan is acceptable.
        var keys = redisTemplate.keys(REDIS_PREFIX + "*");
        if (keys != null) {
            keys.forEach(key -> {
                String val = redisTemplate.opsForValue().get(key);
                if (userId.equals(val)) {
                    redisTemplate.delete(key);
                }
            });
        }
    }

    // ── Utility ───────────────────────────────────────────────

    private String generateRawRefreshToken() {
        // 256-bit cryptographically random token encoded as Base64-URL
        byte[] bytes = new byte[32];
        new java.security.SecureRandom().nextBytes(bytes);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(bytes);
    }

    private String sha256(String input) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(input.getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(hash);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 not available", e);
        }
    }

    private AuthResponse buildResponse(String accessToken, String rawRefresh, AuthUser user) {
        return AuthResponse.builder()
            .accessToken(accessToken)
            .refreshToken(rawRefresh)
            .tokenType("Bearer")
            .accessTokenExpiresIn(jwtProperties.getAccessTokenExpiryMs())
            .issuedAt(Instant.now())
            .userId(user.getId().toString())
            .username(user.getUsername())
            .role(user.getRole().name())
            .build();
    }

    private String extractIp(HttpServletRequest req) {
        String forwarded = req.getHeader("X-Forwarded-For");
        return (forwarded != null) ? forwarded.split(",")[0].trim() : req.getRemoteAddr();
    }

    private String extractUserAgent(HttpServletRequest req) {
        String ua = req.getHeader("User-Agent");
        return (ua != null && ua.length() > 512) ? ua.substring(0, 512) : ua;
    }
}
