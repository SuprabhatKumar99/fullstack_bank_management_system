package com.cbs.customer_service.dto.request;


import java.time.OffsetDateTime;

import com.cbs.customer_service.enums.KycStatus;
import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotNull;
import lombok.Data;

/**
 * Request body for PATCH /api/v1/customers/{customerId}/kyc
 * Used by KYC officers and internal verification systems.
 */
@Data
public class KycStatusUpdateRequest {

    @NotNull(message = "KYC status is required")
    private KycStatus status;

    /** Populated when status = VERIFIED. Typically NOW + 2 years per RBI norms. */
    @Future(message = "KYC expiry must be a future date")
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ssXXX")
    private OffsetDateTime expiresAt;

    /** Rejection reason — required when status = REJECTED. */
    private String rejectionReason;
}