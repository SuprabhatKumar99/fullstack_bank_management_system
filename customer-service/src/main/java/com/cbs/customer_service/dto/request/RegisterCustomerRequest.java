package com.cbs.customer_service.dto.request;

import java.time.LocalDate;
import java.util.UUID;

import com.cbs.customer_service.validation.ValidPan;
import com.cbs.customer_service.enums.CustomerType;
import com.cbs.customer_service.enums.Gender;
import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Past;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for POST /api/v1/customers
 *
 * Validation strategy:
 * - JSR-380 annotations handle format/nullability.
 * - Cross-field rules (individual vs business) are handled in CustomerService.
 * - PAN format validated by custom @ValidPan annotation.
 */
@Data
public class RegisterCustomerRequest {

    @NotNull(message = "Customer type is required")
    private CustomerType customerType;

    // ── Individual ────────────────────────────────────────────────
    @Size(max = 80, message = "First name must not exceed 80 characters")
    private String firstName;

    @Size(max = 80, message = "Last name must not exceed 80 characters")
    private String lastName;

    @JsonFormat(pattern = "yyyy-MM-dd")
    @Past(message = "Date of birth must be in the past")
    private LocalDate dateOfBirth;

    private Gender gender;

    // ── Business ──────────────────────────────────────────────────
    @Size(max = 200, message = "Business name must not exceed 200 characters")
    private String businessName;

    @Size(max = 60, message = "Business type must not exceed 60 characters")
    private String businessType;       // e.g. PROPRIETORSHIP, PRIVATE_LIMITED

    // ── Identity ──────────────────────────────────────────────────
    @ValidPan                          // custom: ^[A-Z]{5}[0-9]{4}[A-Z]$
    private String panNumber;

    // ── Contact ───────────────────────────────────────────────────
    @NotBlank(message = "Phone number is required")
    @Pattern(
        regexp = "^\\+?[0-9]{10,15}$",
        message = "Invalid phone number format"
    )
    private String phone;

    @Email(message = "Invalid email address")
    @Size(max = 254)
    private String email;

    @Pattern(
        regexp = "^\\+?[0-9]{10,15}$",
        message = "Invalid alternate phone format"
    )
    private String alternatePhone;

    // ── Address ───────────────────────────────────────────────────
    @Size(max = 200)
    private String addressLine1;

    @Size(max = 200)
    private String addressLine2;

    @Size(max = 80)
    private String city;

    @Size(max = 80)
    private String state;

    @Pattern(regexp = "^\\d{6}$", message = "Pincode must be 6 digits")
    private String pincode;

    // ── Branch ────────────────────────────────────────────────────
    private UUID homeBranchId;
}