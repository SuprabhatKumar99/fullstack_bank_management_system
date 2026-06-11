package com.cbs.account_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Lightweight balance response — returned by GET /api/v1/accounts/{id}/balance.
 * Served from Redis cache. Intentionally minimal to keep the cache entry small.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceResponse {

    private UUID       accountId;
    private String     accountNumber;
    private String     currency;
    private BigDecimal balance;           // ledger balance
    private BigDecimal holdAmount;
    private BigDecimal overdraftLimit;
    private BigDecimal availableBalance;  // balance - holdAmount + overdraftLimit
    private Boolean    isFrozen;
    private String     status;

    /** Timestamp when this balance was last written to the cache. */
    private OffsetDateTime cachedAt;
}