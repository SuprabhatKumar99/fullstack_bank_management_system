package com.cbs.customer_service.dto.request;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerProfileUpdateRequest {

    @Size(min = 1, max = 100, message = "First name must be between 1 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z\\s'-]+$", message = "First name contains invalid characters")
    private String firstName;

    @Size(min = 1, max = 100, message = "Last name must be between 1 and 100 characters")
    @Pattern(regexp = "^[a-zA-Z\\s'-]+$", message = "Last name contains invalid characters")
    private String lastName;

    @Email(message = "Email must be a valid email address")
    @Size(max = 255)
    private String email;

    @Pattern(regexp = "^\\+?[1-9]\\d{7,14}$", message = "Phone number must be in E.164 format")
    private String phoneNumber;

    @Size(max = 100)
    private String nationality;
}