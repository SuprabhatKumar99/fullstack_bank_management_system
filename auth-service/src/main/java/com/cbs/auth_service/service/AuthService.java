package com.cbs.auth_service.service;


import java.time.Instant;

import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cbs.auth_service.dto.request.LoginRequest;
import com.cbs.auth_service.dto.request.RegisterRequest;
import com.cbs.auth_service.dto.response.AuthEvent;
import com.cbs.auth_service.dto.response.AuthResponse;
import com.cbs.auth_service.entity.AuthUser;
import com.cbs.auth_service.entity.Role;
import com.cbs.auth_service.exception.AccountLockedException;
import com.cbs.auth_service.exception.InvalidCredentialsException;
import com.cbs.auth_service.exception.UserAlreadyExistsException;
import com.cbs.auth_service.repository.AuthUserRepository;
import com.cbs.auth_service.security.JwtService;

import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Handles user registration and login.
 *
 * <p>Responsibilities:
 * <ul>
 *   <li>Validate uniqueness of username/email on registration</li>
 *   <li>Hash and persist credentials</li>
 *   <li>Delegate authentication to Spring Security's {@link AuthenticationManager}</li>
 *   <li>Record login success/failure on the entity (account lockout domain logic)</li>
 *   <li>Delegate token creation to {@link TokenService}</li>
 *   <li>Publish {@link AuthEvent} to Kafka via {@link AuthEventPublisher}</li>
 * </ul>
 *
 * <p>Lockout policy: 5 failed attempts → 30-minute lockout.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthService {

    private static final int  MAX_FAILED_ATTEMPTS = 5;
    private static final int  LOCKOUT_MINUTES     = 30;

    private final AuthUserRepository   authUserRepository;
    private final PasswordEncoder      passwordEncoder;
    private final AuthenticationManager authenticationManager;
    private final JwtService           jwtService;
    private final TokenService         tokenService;
    private final AuthEventPublisher   eventPublisher;

    // ── Registration ──────────────────────────────────────────

    /**
     * Registers a new customer-level user.
     *
     * <p>New users always receive {@link Role#ROLE_CUSTOMER}.
     * Admin or teller accounts are provisioned separately via the admin endpoint.
     *
     * @return a full {@link AuthResponse} so the client is logged in immediately after registration
     */
    @Transactional
    public AuthResponse register(RegisterRequest request, HttpServletRequest httpRequest) {
        if (authUserRepository.existsByUsername(request.getUsername())) {
            throw new UserAlreadyExistsException("Username '" + request.getUsername() + "' is already taken");
        }
        if (authUserRepository.existsByEmail(request.getEmail())) {
            throw new UserAlreadyExistsException("Email '" + request.getEmail() + "' is already registered");
        }

        AuthUser user = AuthUser.builder()
            .username(request.getUsername())
            .email(request.getEmail())
            .password(passwordEncoder.encode(request.getPassword()))
            .role(Role.ROLE_CUSTOMER)
            .build();

        authUserRepository.save(user);
        log.info("New user registered: username={}, id={}", user.getUsername(), user.getId());

        eventPublisher.publish(AuthEvent.builder()
            .eventType(AuthEvent.EventType.USER_REGISTERED)
            .userId(user.getId().toString())
            .username(user.getUsername())
            .ipAddress(extractIp(httpRequest))
            .userAgent(extractUserAgent(httpRequest))
            .occurredAt(Instant.now())
            .build());

        // Issue tokens immediately so the user is logged in right after registration
        return tokenService.issueTokenPair(user, httpRequest);
    }

    // ── Login ─────────────────────────────────────────────────

    /**
     * Authenticates a user by username/email + password.
     *
     * <p>If authentication fails, increments the failed-attempt counter on the entity.
     * After {@value MAX_FAILED_ATTEMPTS} failures the account is locked for
     * {@value LOCKOUT_MINUTES} minutes.
     */
    @Transactional
    public AuthResponse login(LoginRequest request, HttpServletRequest httpRequest) {
        AuthUser user = authUserRepository
            .findByUsername(request.getIdentifier())
            .or(() -> authUserRepository.findByEmail(request.getIdentifier()))
            .orElseThrow(() -> new InvalidCredentialsException("Invalid username/email or password"));

        // Explicit lockout check before hitting AuthenticationManager
        if (!user.isAccountNonLocked()) {
            throw new AccountLockedException(
                "Account is locked. Try again after " + user.getLockedUntil());
        }

        try {
            authenticationManager.authenticate(
                new UsernamePasswordAuthenticationToken(user.getUsername(), request.getPassword())
            );
        } catch (BadCredentialsException ex) {
            handleFailedAttempt(user, httpRequest);
            throw new InvalidCredentialsException("Invalid username/email or password");
        } catch (LockedException ex) {
            throw new AccountLockedException("Account is locked");
        }

        // Successful login — reset counter, issue tokens
        user.recordSuccessfulLogin();
        authUserRepository.save(user);

        eventPublisher.publish(AuthEvent.builder()
            .eventType(AuthEvent.EventType.LOGIN_SUCCESS)
            .userId(user.getId().toString())
            .username(user.getUsername())
            .ipAddress(extractIp(httpRequest))
            .userAgent(extractUserAgent(httpRequest))
            .occurredAt(Instant.now())
            .build());

        log.info("Login success: username={}", user.getUsername());
        return tokenService.issueTokenPair(user, httpRequest);
    }

    // ── Helpers ───────────────────────────────────────────────

    private void handleFailedAttempt(AuthUser user, HttpServletRequest httpRequest) {
        user.recordFailedLogin(MAX_FAILED_ATTEMPTS, LOCKOUT_MINUTES);
        authUserRepository.save(user);

        AuthEvent.EventType eventType = user.isAccountNonLocked()
            ? AuthEvent.EventType.LOGIN_FAILED
            : AuthEvent.EventType.ACCOUNT_LOCKED;

        eventPublisher.publish(AuthEvent.builder()
            .eventType(eventType)
            .userId(user.getId().toString())
            .username(user.getUsername())
            .ipAddress(extractIp(httpRequest))
            .userAgent(extractUserAgent(httpRequest))
            .occurredAt(Instant.now())
            .detail("Failed attempt " + user.getFailedLoginAttempts() + "/" + MAX_FAILED_ATTEMPTS)
            .build());

        log.warn("Login failed: username={}, attempt={}", user.getUsername(), user.getFailedLoginAttempts());
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