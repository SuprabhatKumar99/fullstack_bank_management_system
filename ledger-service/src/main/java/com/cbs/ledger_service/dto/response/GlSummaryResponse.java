package com.cbs.ledger_service.dto.response;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Data;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.UUID;

@Data @Builder @JsonInclude(JsonInclude.Include.NON_NULL)
public class GlSummaryResponse {
    public String     glAccountCode;
    public String     accountType;
    public LocalDate  date;
    public BigDecimal totalDebits;
    public BigDecimal totalCredits;
    public BigDecimal netMovement;
    public int        entryCount;
}