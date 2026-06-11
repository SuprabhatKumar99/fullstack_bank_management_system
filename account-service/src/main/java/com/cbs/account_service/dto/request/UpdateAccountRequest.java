package com.cbs.account_service.dto.request;

import jakarta.validation.constraints.*;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;

/** PATCH /api/v1/accounts/{accountId} — only mutable fields. */
@Data
public class UpdateAccountRequest {

    @Size(max = 120)
    private String nomineeName;

    @Size(max = 60)
    private String nomineeRelation;

    private LocalDate nomineeDob;

    /** Update overdraft limit (CURRENT / OVERDRAFT only). */
    @DecimalMin(value = "0.00")
    @Digits(integer = 16, fraction = 2)
    private BigDecimal overdraftLimit;
}