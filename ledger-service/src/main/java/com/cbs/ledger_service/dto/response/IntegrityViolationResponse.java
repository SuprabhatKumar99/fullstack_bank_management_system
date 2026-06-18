package com.cbs.ledger_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.util.UUID;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class IntegrityViolationResponse {
    public UUID       transactionId;
    public BigDecimal totalDebits;
    public BigDecimal totalCredits;
    public BigDecimal imbalance;    // credits - debits; non-zero = violation
}