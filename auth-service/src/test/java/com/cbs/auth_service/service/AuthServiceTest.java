package com.cbs.auth_service.service;


import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.time.Instant;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.crypto.password.PasswordEncoder;

import com.cbs.auth_service.dto.request.LoginRequest;
import com.cbs.auth_service.dto.request.RegisterRequest;
import com.cbs.auth_service.dto.response.AuthResponse;
import com.cbs.auth_service.entity.AuthUser;
import com.cbs.auth_service.entity.Role;
import com.cbs.auth_service.exception.AccountLockedException;
import com.cbs.auth_service.exception.InvalidCredentialsException;
import com.cbs.auth_service.exception.UserAlreadyExistsException;
import com.cbs.auth_service.repository.AuthUserRepository;
import com.cbs.auth_service.security.JwtService;

import jakarta.servlet.http.HttpServletRequest;

@ExtendWith(MockitoExtension.class)
@DisplayName("AuthService Unit Tests")
class AuthServiceTest {

    @Mock AuthUserRepository    authUserRepository;
    @Mock PasswordEncoder       passwordEncoder;
    @Mock AuthenticationManager authenticationManager;
    @Mock JwtService            jwtService;
    @Mock TokenService          tokenService;
    @Mock AuthEventPublisher    eventPublisher;
    @Mock HttpServletRequest    httpRequest;

    @InjectMocks
    AuthService authService;

    private RegisterRequest registerRequest;
    private LoginRequest    loginRequest;
    private AuthUser        sampleUser;

    @BeforeEach
    void setUp() {
        registerRequest = new RegisterRequest();
        registerRequest.setUsername("john.doe");
        registerRequest.setEmail("john@example.com");
        registerRequest.setPassword("SecurePass@1");

        loginRequest = new LoginRequest();
        loginRequest.setIdentifier("john.doe");
        loginRequest.setPassword("SecurePass@1");

        sampleUser = AuthUser.builder()
            .id(UUID.randomUUID())
            .username("john.doe")
            .email("john@example.com")
            .password("$2a$12$encodedHash")
            .role(Role.ROLE_CUSTOMER)
            .build();
    }

    // ── Registration ──────────────────────────────────────────

    @Test
    @DisplayName("register() → happy path → returns token pair")
    void register_success() {
        when(authUserRepository.existsByUsername("john.doe")).thenReturn(false);
        when(authUserRepository.existsByEmail("john@example.com")).thenReturn(false);
        when(passwordEncoder.encode("SecurePass@1")).thenReturn("$2a$12$encodedHash");
        when(authUserRepository.save(any(AuthUser.class))).thenReturn(sampleUser);
        when(tokenService.issueTokenPair(any(AuthUser.class), any()))
            .thenReturn(AuthResponse.builder()
                .accessToken("access.token.here")
                .refreshToken("raw-refresh-token")
                .tokenType("Bearer")
                .build());

        AuthResponse response = authService.register(registerRequest, httpRequest);

        assertThat(response.getAccessToken()).isEqualTo("access.token.here");
        verify(authUserRepository).save(argThat(u ->
            u.getUsername().equals("john.doe") &&
            u.getRole() == Role.ROLE_CUSTOMER
        ));
        verify(eventPublisher).publish(argThat(e ->
            e.getEventType().name().equals("USER_REGISTERED")
        ));
    }

    @Test
    @DisplayName("register() → duplicate username → throws UserAlreadyExistsException")
    void register_duplicateUsername_throws() {
        when(authUserRepository.existsByUsername("john.doe")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest, httpRequest))
            .isInstanceOf(UserAlreadyExistsException.class)
            .hasMessageContaining("john.doe");

        verify(authUserRepository, never()).save(any());
    }

    @Test
    @DisplayName("register() → duplicate email → throws UserAlreadyExistsException")
    void register_duplicateEmail_throws() {
        when(authUserRepository.existsByUsername("john.doe")).thenReturn(false);
        when(authUserRepository.existsByEmail("john@example.com")).thenReturn(true);

        assertThatThrownBy(() -> authService.register(registerRequest, httpRequest))
            .isInstanceOf(UserAlreadyExistsException.class)
            .hasMessageContaining("john@example.com");
    }

    // ── Login ─────────────────────────────────────────────────

    @Test
    @DisplayName("login() → valid credentials → returns token pair")
    void login_success() {
        when(authUserRepository.findByUsername("john.doe")).thenReturn(Optional.of(sampleUser));
        when(tokenService.issueTokenPair(any(), any()))
            .thenReturn(AuthResponse.builder()
                .accessToken("access.token")
                .refreshToken("refresh.token")
                .tokenType("Bearer")
                .build());

        AuthResponse response = authService.login(loginRequest, httpRequest);

        assertThat(response.getAccessToken()).isEqualTo("access.token");
        verify(authUserRepository).save(sampleUser); // saved after recordSuccessfulLogin()
    }

    @Test
    @DisplayName("login() → wrong password → throws InvalidCredentialsException")
    void login_badPassword_throws() {
        when(authUserRepository.findByUsername("john.doe")).thenReturn(Optional.of(sampleUser));
        doThrow(new BadCredentialsException("bad"))
            .when(authenticationManager).authenticate(any(UsernamePasswordAuthenticationToken.class));

        assertThatThrownBy(() -> authService.login(loginRequest, httpRequest))
            .isInstanceOf(InvalidCredentialsException.class);

        // Failed attempt must be persisted
        verify(authUserRepository).save(sampleUser);
    }

    @Test
    @DisplayName("login() → unknown identifier → throws InvalidCredentialsException")
    void login_unknownUser_throws() {
        when(authUserRepository.findByUsername("john.doe")).thenReturn(Optional.empty());
        when(authUserRepository.findByEmail("john.doe")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> authService.login(loginRequest, httpRequest))
            .isInstanceOf(InvalidCredentialsException.class);
    }

    @Test
    @DisplayName("login() → locked account → throws AccountLockedException before authenticate()")
    void login_lockedAccount_throws() {
        AuthUser lockedUser = AuthUser.builder()
            .id(UUID.randomUUID())
            .username("john.doe")
            .email("john@example.com")
            .password("hash")
            .role(Role.ROLE_CUSTOMER)
            .accountNonLocked(false)
            .lockedUntil(Instant.now().plusSeconds(3600))
            .build();

        when(authUserRepository.findByUsername("john.doe")).thenReturn(Optional.of(lockedUser));

        assertThatThrownBy(() -> authService.login(loginRequest, httpRequest))
            .isInstanceOf(AccountLockedException.class);

        // AuthenticationManager must NOT be called for a locked account
        verify(authenticationManager, never()).authenticate(any());
    }

    @Test
    @DisplayName("login() → email identifier → resolves by email lookup")
    void login_byEmail_success() {
        loginRequest.setIdentifier("john@example.com");

        when(authUserRepository.findByUsername("john@example.com")).thenReturn(Optional.empty());
        when(authUserRepository.findByEmail("john@example.com")).thenReturn(Optional.of(sampleUser));
        when(tokenService.issueTokenPair(any(), any()))
            .thenReturn(AuthResponse.builder().accessToken("tok").build());

        AuthResponse response = authService.login(loginRequest, httpRequest);

        assertThat(response.getAccessToken()).isEqualTo("tok");
    }
}