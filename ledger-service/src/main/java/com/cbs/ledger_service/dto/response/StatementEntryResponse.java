package com.cbs.ledger_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/** One row in the account passbook / statement view. */
@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class StatementEntryResponse {
    private UUID       entryId;
    private UUID       transactionId;
    private UUID       accountId;
    private String     entryType;        // DEBIT | CREDIT
    private BigDecimal debitAmount;      // populated only for DEBIT entries
    private BigDecimal creditAmount;     // populated only for CREDIT entries
    private BigDecimal balanceAfter;     // running balance after this entry
    private String     currency;
    private String     glAccountCode;
    private String     narration;
    private LocalDate  valueDate;
    private OffsetDateTime createdAt;
}