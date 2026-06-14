package com.cbs.auth_service.dto.request;


import jakarta.validation.constraints.*;
import lombok.Data;

/**
 * Request body for {@code POST /api/auth/register}.
 */
@Data
public class RegisterRequest {

    @NotBlank(message = "Username is required")
    @Size(min = 3, max = 50, message = "Username must be 3–50 characters")
    @Pattern(regexp = "^[a-zA-Z0-9._-]+$",
             message = "Username may only contain letters, digits, dots, hyphens, and underscores")
    private String username;

    @NotBlank(message = "Email is required")
    @Email(message = "Email must be a valid address")
    @Size(max = 100)
    private String email;

    @NotBlank(message = "Password is required")
    @Size(min = 8, max = 72, message = "Password must be 8–72 characters")
    @Pattern(regexp = "^(?=.*[a-z])(?=.*[A-Z])(?=.*\\d)(?=.*[@$!%*?&]).+$",
             message = "Password must contain uppercase, lowercase, digit, and special character")
    private String password;
}