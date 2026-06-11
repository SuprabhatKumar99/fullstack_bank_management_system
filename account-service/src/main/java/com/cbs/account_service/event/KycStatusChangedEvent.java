package com.cbs.account_service.event;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Consumed from topic: customer.kyc.status.changed
 * When KYC is VERIFIED  → unfreeze all accounts for this customer.
 * When KYC is REJECTED / EXPIRED → freeze all accounts.
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
class KycStatusChangedEvent {
    private UUID   customerId;
    private String previousStatus;
    private String newStatus;
    private String changedBy;
    private OffsetDateTime occurredAt;
}
