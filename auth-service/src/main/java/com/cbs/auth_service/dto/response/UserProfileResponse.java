package com.cbs.auth_service.dto.response;


import lombok.Builder;
import lombok.Data;

import java.time.Instant;
import java.util.UUID;

/**
 * Returned by {@code GET /api/auth/me}.
 * Never exposes the password hash or internal lockout details.
 */
@Data
@Builder
public class UserProfileResponse {

    private UUID    id;
    private String  username;
    private String  email;
    private String  role;
    private UUID    customerId;
    private boolean enabled;
    private Instant lastLoginAt;
    private Instant createdAt;
}