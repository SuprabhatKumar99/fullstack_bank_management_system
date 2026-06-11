package com.cbs.account_service.event;

import java.time.OffsetDateTime;
import java.util.UUID;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published to topic: account.closed
 * Consumed by: Card Service (block linked debit cards).
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AccountClosedEvent {
    private UUID   accountId;
    private String accountNumber;
    private UUID   customerId;
    private String closureReason;
    private OffsetDateTime occurredAt;
}