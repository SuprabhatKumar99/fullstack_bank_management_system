package com.cbs.auth_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doNothing;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.time.Instant;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.test.context.support.WithMockUser;
import org.springframework.test.web.servlet.MockMvc;

import com.cbs.auth_service.config.SecurityConfig;
import com.cbs.auth_service.dto.request.LoginRequest;
import com.cbs.auth_service.dto.request.RefreshRequest;
import com.cbs.auth_service.dto.request.RegisterRequest;
import com.cbs.auth_service.dto.response.AuthResponse;
import com.cbs.auth_service.dto.response.UserProfileResponse;
import com.cbs.auth_service.entity.AuthUser;
import com.cbs.auth_service.entity.Role;
import com.cbs.auth_service.exception.InvalidCredentialsException;
import com.cbs.auth_service.exception.UserAlreadyExistsException;
import com.cbs.auth_service.filter.JwtAuthenticationFilter;
import com.cbs.auth_service.mapper.AuthUserMapper;
import com.cbs.auth_service.repository.AuthUserRepository;
import com.cbs.auth_service.security.JwtService;
import com.cbs.auth_service.service.AuthService;
import com.cbs.auth_service.service.TokenService;
import com.cbs.auth_service.service.UserDetailsServiceImpl;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(AuthController.class)
@Import({SecurityConfig.class, JwtAuthenticationFilter.class})
@DisplayName("AuthController MockMvc Tests")
class AuthControllerTest {

    @Autowired MockMvc       mockMvc;
    @Autowired ObjectMapper  objectMapper;

    @MockBean AuthService          authService;
    @MockBean TokenService         tokenService;
    @MockBean AuthUserRepository   authUserRepository;
    @MockBean AuthUserMapper       authUserMapper;
    @MockBean JwtService           jwtService;
    @MockBean UserDetailsServiceImpl userDetailsService;

    private AuthResponse sampleAuthResponse;
    private AuthUser     sampleUser;

    @BeforeEach
    void setUp() {
        sampleAuthResponse = AuthResponse.builder()
            .accessToken("eyJhbGciOiJIUzI1NiJ9.access")
            .refreshToken("raw-refresh-token-abc123")
            .tokenType("Bearer")
            .accessTokenExpiresIn(900_000L)
            .issuedAt(Instant.now())
            .userId(UUID.randomUUID().toString())
            .username("john.doe")
            .role("ROLE_CUSTOMER")
            .build();

        sampleUser = AuthUser.builder()
            .id(UUID.randomUUID())
            .username("john.doe")
            .email("john@example.com")
            .role(Role.ROLE_CUSTOMER)
            .build();
    }

    // ── POST /auth/register ───────────────────────────────────

    @Test
    @DisplayName("POST /auth/register → 201 Created with token pair")
    void register_returns201() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("john.doe");
        request.setEmail("john@example.com");
        request.setPassword("SecurePass@1");

        when(authService.register(any(), any())).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.tokenType").value("Bearer"))
            .andExpect(jsonPath("$.username").value("john.doe"));
    }

    @Test
    @DisplayName("POST /auth/register → 400 when username blank")
    void register_blankUsername_returns400() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("");
        request.setEmail("john@example.com");
        request.setPassword("SecurePass@1");

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.fieldErrors.username").exists());
    }

    @Test
    @DisplayName("POST /auth/register → 409 when username taken")
    void register_duplicateUsername_returns409() throws Exception {
        RegisterRequest request = new RegisterRequest();
        request.setUsername("john.doe");
        request.setEmail("john@example.com");
        request.setPassword("SecurePass@1");

        when(authService.register(any(), any()))
            .thenThrow(new UserAlreadyExistsException("Username 'john.doe' is already taken"));

        mockMvc.perform(post("/auth/register")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.message").value("Username 'john.doe' is already taken"));
    }

    // ── POST /auth/login ──────────────────────────────────────

    @Test
    @DisplayName("POST /auth/login → 200 OK with token pair")
    void login_returns200() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setIdentifier("john.doe");
        request.setPassword("SecurePass@1");

        when(authService.login(any(), any())).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists())
            .andExpect(jsonPath("$.role").value("ROLE_CUSTOMER"));
    }

    @Test
    @DisplayName("POST /auth/login → 401 on bad credentials")
    void login_badCredentials_returns401() throws Exception {
        LoginRequest request = new LoginRequest();
        request.setIdentifier("john.doe");
        request.setPassword("WrongPass@1");

        when(authService.login(any(), any()))
            .thenThrow(new InvalidCredentialsException("Invalid username/email or password"));

        mockMvc.perform(post("/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isUnauthorized())
            .andExpect(jsonPath("$.status").value(401));
    }

    // ── POST /auth/refresh ────────────────────────────────────

    @Test
    @DisplayName("POST /auth/refresh → 200 OK with new token pair")
    void refresh_returns200() throws Exception {
        RefreshRequest request = new RefreshRequest();
        request.setRefreshToken("valid-refresh-token");

        when(tokenService.rotateRefreshToken(any(), any())).thenReturn(sampleAuthResponse);

        mockMvc.perform(post("/auth/refresh")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.accessToken").exists());
    }

    // ── GET /auth/me ──────────────────────────────────────────

    @Test
    @DisplayName("GET /auth/me → 200 OK with user profile when authenticated")
    @WithMockUser(username = "john.doe", roles = "CUSTOMER")
    void getMe_authenticated_returns200() throws Exception {
        UserProfileResponse profile = UserProfileResponse.builder()
            .id(UUID.randomUUID())
            .username("john.doe")
            .email("john@example.com")
            .role("ROLE_CUSTOMER")
            .enabled(true)
            .build();

        when(authUserMapper.toProfileResponse(any())).thenReturn(profile);

        mockMvc.perform(get("/auth/me"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.username").value("john.doe"))
            .andExpect(jsonPath("$.email").value("john@example.com"));
    }

    @Test
    @DisplayName("GET /auth/me → 401 without token")
    void getMe_unauthenticated_returns401() throws Exception {
        mockMvc.perform(get("/auth/me"))
            .andExpect(status().isUnauthorized());
    }

    // ── POST /auth/logout ─────────────────────────────────────

    @Test
    @DisplayName("POST /auth/logout → 204 No Content")
    @WithMockUser(username = "john.doe", roles = "CUSTOMER")
    void logout_returns204() throws Exception {
        doNothing().when(tokenService).revokeAllTokensForUser(any(), any());

        mockMvc.perform(post("/auth/logout"))
            .andExpect(status().isNoContent());
    }
}