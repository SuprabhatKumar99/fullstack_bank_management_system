package com.cbs.auth_service.security;

import java.nio.charset.StandardCharsets;
import java.time.Instant;
import java.util.Date;
import java.util.Map;
import java.util.UUID;

import javax.crypto.SecretKey;

import org.springframework.stereotype.Service;

import com.cbs.auth_service.config.JwtProperties;
import com.cbs.auth_service.entity.AuthUser;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Core JWT operations: generation, validation, and claims extraction.
 *
 * <p>Uses HMAC-SHA256 (HS256) with a symmetric key loaded from {@link JwtProperties}.
 * In a multi-service production deployment, consider RS256 with a public/private key pair
 * so that resource servers can verify tokens without sharing the secret.
 *
 * <p>Custom claims embedded in the access token:
 * <ul>
 *   <li>{@code role}        – single role string (e.g. "ROLE_CUSTOMER")</li>
 *   <li>{@code customerId}  – UUID of the linked customer profile (may be null)</li>
 *   <li>{@code userId}      – UUID of the auth_users record</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class JwtService {

    private final JwtProperties jwtProperties;

    // ── Access Token ──────────────────────────────────────────

    /**
     * Generates a signed access token for the given user.
     * Contains {@code sub} (username), {@code role}, {@code userId}, {@code customerId}.
     */
    public String generateAccessToken(AuthUser user) {
        Instant now = Instant.now();
        Instant expiry = now.plusMillis(jwtProperties.getAccessTokenExpiryMs());

        Map<String, Object> extraClaims = Map.of(
            "role",       user.getRole().name(),
            "userId",     user.getId().toString(),
            "customerId", user.getCustomerId() != null ? user.getCustomerId().toString() : ""
        );

        return Jwts.builder()
            .id(UUID.randomUUID().toString())        // jti – unique token id
            .issuer(jwtProperties.getIssuer())
            .subject(user.getUsername())
            .claims(extraClaims)
            .issuedAt(Date.from(now))
            .expiration(Date.from(expiry))
            .signWith(signingKey())
            .compact();
    }

    // ── Validation ────────────────────────────────────────────

    public boolean isTokenValid(String token) {
        try {
            parseClaims(token);
            return true;
        } catch (JwtException | IllegalArgumentException ex) {
            log.debug("JWT validation failed: {}", ex.getMessage());
            return false;
        }
    }

    // ── Claims Extraction ─────────────────────────────────────

    public String extractUsername(String token) {
        return parseClaims(token).getSubject();
    }

    public String extractRole(String token) {
        return (String) parseClaims(token).get("role");
    }

    public String extractUserId(String token) {
        return (String) parseClaims(token).get("userId");
    }

    public boolean isTokenExpired(String token) {
        return parseClaims(token).getExpiration().before(new Date());
    }

    public Instant extractExpiry(String token) {
        return parseClaims(token).getExpiration().toInstant();
    }

    // ── Internals ─────────────────────────────────────────────

    private Claims parseClaims(String token) {
        return Jwts.parser()
            .verifyWith(signingKey())
            .build()
            .parseSignedClaims(token)
            .getPayload();
    }

    private SecretKey signingKey() {
        byte[] keyBytes = jwtProperties.getSecret().getBytes(StandardCharsets.UTF_8);
        return Keys.hmacShaKeyFor(keyBytes);
    }
}