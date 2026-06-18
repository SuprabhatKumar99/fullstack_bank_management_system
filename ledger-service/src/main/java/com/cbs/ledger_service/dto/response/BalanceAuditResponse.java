package com.cbs.ledger_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class BalanceAuditResponse {
    public UUID       accountId;
    public BigDecimal cachedBalance;
    public BigDecimal ledgerBalance;
    public BigDecimal discrepancy;
    public boolean    isBalanced;
    public OffsetDateTime auditedAt;
}