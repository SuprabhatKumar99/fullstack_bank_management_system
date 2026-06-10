package com.cbs.customer_service.dto.request;


import com.cbs.customer_service.enums.Gender;

import jakarta.validation.constraints.Email;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for PATCH /api/v1/customers/{customerId}/profile
 * Only mutable fields are exposed — customerId, PAN, phone are immutable.
 */
@Data
public class UpdateProfileRequest {

    @Size(max = 80)
    private String firstName;

    @Size(max = 80)
    private String lastName;

    private Gender gender;

    @Email(message = "Invalid email address")
    @Size(max = 254)
    private String email;

    @Pattern(regexp = "^\\+?[0-9]{10,15}$", message = "Invalid alternate phone format")
    private String alternatePhone;

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

    @Size(max = 200)
    private String businessName;

    @Size(max = 60)
    private String businessType;
}