package com.cbs.account_service.event;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Consumed from topic: customer.registered
 * Used to verify the customer exists before opening an account.
 * In production, the Account Service would call the Customer Service HTTP API
 * or rely on this event to pre-populate a local customer_kyc_status table.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
class CustomerRegisteredEvent {
    private UUID   customerId;
    private String customerType;
    private String displayName;
    private String phone;
    private UUID   homeBranchId;
    private OffsetDateTime occurredAt;
}