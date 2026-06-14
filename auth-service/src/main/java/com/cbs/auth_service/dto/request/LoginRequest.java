package com.cbs.auth_service.dto.request;


import jakarta.validation.constraints.NotBlank;
import lombok.Data;

/**
 * Request body for {@code POST /api/auth/login}.
 * Accepts either a username or email in the {@code identifier} field.
 */
@Data
public class LoginRequest {

    @NotBlank(message = "Identifier (username or email) is required")
    private String identifier;

    @NotBlank(message = "Password is required")
    private String password;
}