package com.cbs.account_service.event;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published to topic: account.frozen
 * Consumed by: Fraud Detection Service (audit trail).
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AccountFrozenEvent {
    private UUID    accountId;
    private String  accountNumber;
    private UUID    customerId;
    private boolean frozen;       // true = frozen, false = unfrozen
    private String  reason;
    private OffsetDateTime occurredAt;
}