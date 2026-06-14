package com.cbs.auth_service.dto.response;


import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Returned by login and refresh endpoints.
 * The client stores the {@code accessToken} in memory (not localStorage)
 * and the {@code refreshToken} in an HttpOnly cookie (handled by the gateway layer).
 */
@Data
@Builder
public class AuthResponse {

    private String accessToken;
    private String refreshToken;
    private String tokenType;           // always "Bearer"
    private long   accessTokenExpiresIn; // milliseconds
    private Instant issuedAt;

    private String  userId;
    private String  username;
    private String  role;
}