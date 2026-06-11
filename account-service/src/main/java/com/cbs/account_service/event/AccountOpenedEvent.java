package com.cbs.account_service.event;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.cbs.account_service.enums.AccountType;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Published to topic: account.opened
 * Consumed by: Notification Service (welcome SMS), Card Service (issue debit card).
 */
@Data @Builder @NoArgsConstructor @AllArgsConstructor
public class AccountOpenedEvent {
    private UUID        accountId;
    private String      accountNumber;
    private UUID        customerId;
    private UUID        branchId;
    private AccountType accountType;
    private String      currency;
    private BigDecimal  minimumBalance;
    private OffsetDateTime occurredAt;
}