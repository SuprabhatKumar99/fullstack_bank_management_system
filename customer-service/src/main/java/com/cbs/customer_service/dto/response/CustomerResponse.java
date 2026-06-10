package com.cbs.customer_service.dto.response;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.cbs.customer_service.enums.CustomerType;
import com.cbs.customer_service.enums.Gender;
import com.cbs.customer_service.enums.KycStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

/**
 * Full customer profile response — returned by GET /api/v1/customers/{id}.
 * Sensitive fields (full Aadhaar, internal flags) are excluded.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerResponse {

    private UUID         customerId;
    private CustomerType customerType;

    // Individual
    private String     firstName;
    private String     lastName;
    private String     displayName;
    private LocalDate  dateOfBirth;
    private Gender     gender;

    // Business
    private String businessName;
    private String businessType;

    // Identity (masked)
    private String panNumber;          // always shown (PAN is semi-public)
    private String aadhaarLastFour;    // XXXX-XXXX-1234 — only last 4 digits

    // Contact
    private String phone;
    private String email;
    private String alternatePhone;

    // Address
    private String addressLine1;
    private String addressLine2;
    private String city;
    private String state;
    private String pincode;
    private String country;

    // KYC
    private KycStatus      kycStatus;
    private OffsetDateTime kycVerifiedAt;
    private OffsetDateTime kycExpiresAt;

    // Risk
    private String  riskCategory;
    private Boolean isPep;

    // Branch
    private UUID homeBranchId;

    // Audit
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}