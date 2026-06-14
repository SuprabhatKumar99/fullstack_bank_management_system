package com.cbs.auth_service.controller;

import java.util.UUID;

import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.cbs.auth_service.dto.request.LoginRequest;
import com.cbs.auth_service.dto.request.RefreshRequest;
import com.cbs.auth_service.dto.request.RegisterRequest;
import com.cbs.auth_service.dto.response.AuthResponse;
import com.cbs.auth_service.dto.response.UserProfileResponse;
import com.cbs.auth_service.entity.AuthUser;
import com.cbs.auth_service.mapper.AuthUserMapper;
import com.cbs.auth_service.repository.AuthUserRepository;
import com.cbs.auth_service.service.AuthService;
import com.cbs.auth_service.service.TokenService;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for all authentication operations.
 *
 * <p>Base path: {@code /api/auth}
 *
 * <h3>Public endpoints (no token required)</h3>
 * <ul>
 *   <li>{@code POST /api/auth/register}  – create account + receive tokens</li>
 *   <li>{@code POST /api/auth/login}     – authenticate + receive tokens</li>
 *   <li>{@code POST /api/auth/refresh}   – rotate refresh token</li>
 * </ul>
 *
 * <h3>Authenticated endpoints</h3>
 * <ul>
 *   <li>{@code GET  /api/auth/me}        – current user profile</li>
 *   <li>{@code POST /api/auth/logout}    – revoke all sessions</li>
 * </ul>
 *
 * <h3>Admin-only endpoints</h3>
 * <ul>
 *   <li>{@code DELETE /api/auth/admin/users/{userId}/sessions} – force-logout any user</li>
 * </ul>
 */
@RestController
@RequestMapping("/auth")
@RequiredArgsConstructor
@Slf4j
public class AuthController {

    private final AuthService         authService;
    private final TokenService        tokenService;
    private final AuthUserRepository  authUserRepository;
    private final AuthUserMapper      authUserMapper;

    // ── POST /auth/register ───────────────────────────────────

    /**
     * Registers a new customer account.
     * Returns a full token pair — the client is immediately authenticated.
     */
    @PostMapping("/register")
    public ResponseEntity<AuthResponse> register(
            @Valid @RequestBody RegisterRequest request,
            HttpServletRequest httpRequest) {

        AuthResponse response = authService.register(request, httpRequest);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    // ── POST /auth/login ──────────────────────────────────────

    /**
     * Authenticates with username/email + password.
     * Returns an access token (15 min) and a refresh token (7 days).
     */
    @PostMapping("/login")
    public ResponseEntity<AuthResponse> login(
            @Valid @RequestBody LoginRequest request,
            HttpServletRequest httpRequest) {

        AuthResponse response = authService.login(request, httpRequest);
        return ResponseEntity.ok(response);
    }

    // ── POST /auth/refresh ────────────────────────────────────

    /**
     * Rotates the refresh token.
     * The old token is immediately revoked; a new pair is issued.
     * Presenting a previously-revoked token triggers full session invalidation.
     */
    @PostMapping("/refresh")
    public ResponseEntity<AuthResponse> refresh(
            @Valid @RequestBody RefreshRequest request,
            HttpServletRequest httpRequest) {

        AuthResponse response = tokenService.rotateRefreshToken(request.getRefreshToken(), httpRequest);
        return ResponseEntity.ok(response);
    }

    // ── GET /auth/me ──────────────────────────────────────────

    /**
     * Returns the profile of the currently authenticated user.
     * Requires a valid access token in the Authorization header.
     */
    @GetMapping("/me")
    public ResponseEntity<UserProfileResponse> getCurrentUser(
            @AuthenticationPrincipal AuthUser currentUser) {

        return ResponseEntity.ok(authUserMapper.toProfileResponse(currentUser));
    }

    // ── POST /auth/logout ─────────────────────────────────────

    /**
     * Revokes all active refresh tokens for the current user.
     * The client should discard its access token locally (it remains valid until expiry).
     */
    @PostMapping("/logout")
    public ResponseEntity<Void> logout(
            @AuthenticationPrincipal AuthUser currentUser,
            HttpServletRequest httpRequest) {

        tokenService.revokeAllTokensForUser(currentUser.getId().toString(), httpRequest);
        return ResponseEntity.noContent().build();
    }

    // ── DELETE /auth/admin/users/{userId}/sessions ────────────

    /**
     * Admin endpoint: force-revoke all sessions for any user.
     * Use case: suspected account compromise, disciplinary action.
     */
    @DeleteMapping("/admin/users/{userId}/sessions")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<Void> revokeUserSessions(
            @PathVariable UUID userId,
            HttpServletRequest httpRequest) {

        // Verify user exists before revoking
        authUserRepository.findById(userId)
            .orElseThrow(() -> new com.cbs.auth_service.exception.InvalidTokenException(
                "User not found: " + userId));

        tokenService.revokeAllTokensForUser(userId.toString(), httpRequest);
        log.info("Admin force-logout: targetUserId={}", userId);
        return ResponseEntity.noContent().build();
    }
}