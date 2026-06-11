package com.cbs.account_service.dto.response;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import com.cbs.account_service.enums.AccountStatus;
import com.cbs.account_service.enums.AccountType;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

/** Full account profile — returned by GET /api/v1/accounts/{accountId} */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class AccountResponse {

    private UUID          accountId;
    private String        accountNumber;
    private UUID          customerId;
    private UUID          branchId;
    private AccountType   accountType;
    private String        currency;

    // Balance snapshot
    private BigDecimal    balance;
    private BigDecimal    holdAmount;
    private BigDecimal    overdraftLimit;
    private BigDecimal    availableBalance;     // computed: balance - hold + overdraft
    private BigDecimal    minimumBalance;

    // Interest
    private BigDecimal    interestRate;
    private LocalDate     lastInterestCalcDate;

    // Status
    private AccountStatus status;
    private Boolean       isFrozen;
    private OffsetDateTime activatedAt;
    private LocalDate     dormantSince;
    private OffsetDateTime closedAt;
    private String        closureReason;

    // Term deposit
    private LocalDate     maturityDate;
    private BigDecimal    maturityAmount;
    private UUID          parentAccountId;

    // Nominee
    private String        nomineeName;
    private String        nomineeRelation;
    private LocalDate     nomineeDob;

    // Flags
    private Boolean       isJointAccount;
    private Boolean       isNriAccount;

    // Audit
    private UUID          createdBy;
    private OffsetDateTime createdAt;
    private OffsetDateTime updatedAt;
}