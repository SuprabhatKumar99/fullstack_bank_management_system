package com.cbs.account_service.dto.request;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

import com.cbs.account_service.enums.AccountType;
import com.fasterxml.jackson.annotation.JsonFormat;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Digits;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Positive;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * Request body for POST /api/v1/accounts
 *
 * Cross-field rules enforced in AccountService:
 * - FD/RD must provide tenureMonths and parentAccountId
 * - CURRENT/OVERDRAFT may provide overdraftLimit
 * - LOAN requires loanAmount
 */
@Data
public class OpenAccountRequest {

    @NotNull(message = "Customer ID is required")
    private UUID customerId;

    @NotNull(message = "Branch ID is required")
    private UUID branchId;

    @NotNull(message = "Account type is required")
    private AccountType accountType;

    @Size(max = 3, min = 3, message = "Currency must be a 3-letter ISO code")
    @Pattern(regexp = "^[A-Z]{3}$", message = "Currency must be uppercase ISO code e.g. INR")
    private String currency = "INR";

    // ── Term Deposit fields (FD / RD) ─────────────────────────────
    @Positive(message = "Tenure must be positive")
    private Integer tenureMonths;           // mandatory for FD / RD

    private UUID parentAccountId;           // mandatory for FD / RD

    @DecimalMin(value = "0.00", message = "Initial deposit must be non-negative")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal initialDeposit;      // mandatory for FD; optional for SAVINGS/CURRENT

    // ── Overdraft (CURRENT / OVERDRAFT) ──────────────────────────
    @DecimalMin(value = "0.00", message = "Overdraft limit must be non-negative")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal overdraftLimit;

    // ── Nominee ───────────────────────────────────────────────────
    @Size(max = 120)
    private String nomineeName;

    @Size(max = 60)
    private String nomineeRelation;

    @JsonFormat(pattern = "yyyy-MM-dd")
    private LocalDate nomineeDob;

    // ── Flags ─────────────────────────────────────────────────────
    private Boolean isJointAccount = false;
    private Boolean isNriAccount   = false;
}