package com.cbs.customer_service.config;


import java.time.OffsetDateTime;
import java.util.UUID;

import com.cbs.customer_service.enums.KycStatus;

import lombok.Builder;
import lombok.Data;

/**
 * Kafka event published when a customer's KYC status changes.
 * Topic: customer.kyc.status.changed
 *
 * Consumed by: Account Service (unblock/block account operations),
 *              Notification Service (alert customer),
 *              Fraud Detection (risk re-scoring).
 */
@Data
@Builder
public class KycStatusChangedEvent {
    private UUID           customerId;
    private KycStatus      previousStatus;
    private KycStatus      newStatus;
    private String         changedBy;        // officer ID or "SYSTEM"
    private String         rejectionReason;  // populated when REJECTED
    private OffsetDateTime expiresAt;        // populated when VERIFIED
    private OffsetDateTime occurredAt;
}