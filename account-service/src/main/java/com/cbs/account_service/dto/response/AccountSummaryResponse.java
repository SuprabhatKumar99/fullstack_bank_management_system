package com.cbs.account_service.dto.response;

import java.math.BigDecimal;
import java.util.UUID;

import com.cbs.account_service.enums.AccountStatus;
import com.cbs.account_service.enums.AccountType;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

/** Lightweight projection for list endpoints. */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountSummaryResponse {
    private UUID          accountId;
    private String        accountNumber;
    private AccountType   accountType;
    private String        currency;
    private BigDecimal    balance;
    private BigDecimal    availableBalance;
    private AccountStatus status;
    private Boolean       isFrozen;
}