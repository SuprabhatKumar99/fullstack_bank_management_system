package com.cbs.ledger_service.service;


import com.cbs.ledger.dto.response.BalanceAuditResponse;
import com.cbs.ledger.dto.response.GlSummaryResponse;
import com.cbs.ledger.dto.response.IntegrityViolationResponse;
import com.cbs.ledger.dto.response.StatementEntryResponse;
import com.cbs.ledger.entity.LedgerEntry;
import com.cbs.ledger.repository.LedgerEntryRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("LedgerService")
class LedgerServiceTest {

    @Mock LedgerEntryRepository repo;
    @InjectMocks LedgerService  ledgerService;

    private final UUID accountId = UUID.randomUUID();
    private final UUID txnId     = UUID.randomUUID();

    private LedgerEntry entry(String type, BigDecimal amount, String gl) {
        return LedgerEntry.builder()
            .entryId(UUID.randomUUID())
            .transactionId(txnId)
            .accountId(accountId)
            .entryType(type)
            .amount(amount)
            .balanceAfter(new BigDecimal("9500.00"))
            .currency("INR")
            .accountType("LIABILITY")
            .glAccountCode(gl)
            .narration("test entry")
            .valueDate(LocalDate.now())
            .createdAt(OffsetDateTime.now())
            .build();
    }

    @Nested @DisplayName("getStatement()")
    class StatementTests {

        @Test
        @DisplayName("should return paginated statement entries")
        void returnsEntries() {
            LedgerEntry credit = entry("CREDIT", new BigDecimal("10000.00"), "2001");
            LedgerEntry debit  = entry("DEBIT",  new BigDecimal("500.00"),   "2001");

            given(repo.findByAccountIdAndValueDateBetweenOrderByCreatedAtDesc(
                eq(accountId), any(), any(), any()))
                .willReturn(new PageImpl<>(List.of(credit, debit)));

            var page = ledgerService.getStatement(
                accountId, LocalDate.now().minusDays(30), LocalDate.now(),
                PageRequest.of(0, 20));

            assertThat(page.getContent()).hasSize(2);
            // CREDIT entry should have creditAmount set, debitAmount null
            StatementEntryResponse creditRow = page.getContent().get(0);
            assertThat(creditRow.getCreditAmount()).isEqualByComparingTo("10000.00");
            assertThat(creditRow.getDebitAmount()).isNull();

            // DEBIT entry should have debitAmount set, creditAmount null
            StatementEntryResponse debitRow = page.getContent().get(1);
            assertThat(debitRow.getDebitAmount()).isEqualByComparingTo("500.00");
            assertThat(debitRow.getCreditAmount()).isNull();
        }
    }

    @Nested @DisplayName("auditBalance()")
    class AuditTests {

        @Test
        @DisplayName("should return isBalanced=true when ledger matches cache")
        void balanced() {
            given(repo.computeLedgerBalance(accountId))
                .willReturn(new BigDecimal("10000.00"));

            BalanceAuditResponse result =
                ledgerService.auditBalance(accountId, new BigDecimal("10000.00"));

            assertThat(result.isBalanced).isTrue();
            assertThat(result.discrepancy).isEqualByComparingTo("0.00");
        }

        @Test
        @DisplayName("should return isBalanced=false when discrepancy exists")
        void imbalanced() {
            given(repo.computeLedgerBalance(accountId))
                .willReturn(new BigDecimal("9500.00"));

            BalanceAuditResponse result =
                ledgerService.auditBalance(accountId, new BigDecimal("10000.00"));

            assertThat(result.isBalanced).isFalse();
            assertThat(result.discrepancy).isEqualByComparingTo("500.00");
            assertThat(result.ledgerBalance).isEqualByComparingTo("9500.00");
            assertThat(result.cachedBalance).isEqualByComparingTo("10000.00");
        }
    }

    @Nested @DisplayName("findIntegrityViolations()")
    class IntegrityTests {

        @Test
        @DisplayName("should return empty list when all transactions are balanced")
        void noViolations() {
            LedgerEntry debit  = entry("DEBIT",  new BigDecimal("500.00"), "2001");
            LedgerEntry credit = entry("CREDIT", new BigDecimal("500.00"), "2001");

            given(repo.findByValueDate(any())).willReturn(List.of(debit, credit));

            List<IntegrityViolationResponse> violations =
                ledgerService.findIntegrityViolations(LocalDate.now());

            // Same txnId, DEBIT 500 == CREDIT 500 → balanced
            assertThat(violations).isEmpty();
        }

        @Test
        @DisplayName("should detect imbalanced transaction")
        void detectsViolation() {
            // Only a DEBIT, no matching CREDIT — clear violation
            LedgerEntry onlyDebit = entry("DEBIT", new BigDecimal("500.00"), "2001");

            UUID txn2 = UUID.randomUUID();
            LedgerEntry entry2 = LedgerEntry.builder()
                .entryId(UUID.randomUUID()).transactionId(txn2)
                .accountId(accountId).entryType("DEBIT")
                .amount(new BigDecimal("300.00")).balanceAfter(BigDecimal.ZERO)
                .currency("INR").accountType("LIABILITY")
                .glAccountCode("2001").valueDate(LocalDate.now())
                .createdAt(OffsetDateTime.now()).build();

            given(repo.findByValueDate(any())).willReturn(List.of(onlyDebit, entry2));

            List<IntegrityViolationResponse> violations =
                ledgerService.findIntegrityViolations(LocalDate.now());

            // Both transactions have debits but no credits
            assertThat(violations).hasSize(2);
            violations.forEach(v ->
                assertThat(v.imbalance).isNegative()); // credits - debits < 0
        }
    }

    @Nested @DisplayName("getDailySummary()")
    class GlSummaryTests {

        @Test
        @DisplayName("should aggregate correctly by GL code")
        void aggregatesByGlCode() {
            LedgerEntry e1 = entry("DEBIT",  new BigDecimal("1000.00"), "2001");
            LedgerEntry e2 = entry("CREDIT", new BigDecimal("1000.00"), "2001");
            LedgerEntry e3 = entry("CREDIT", new BigDecimal("500.00"),  "4002");

            given(repo.findByValueDate(LocalDate.now()))
                .willReturn(List.of(e1, e2, e3));

            List<GlSummaryResponse> summary =
                ledgerService.getDailySummary(LocalDate.now());

            assertThat(summary).hasSize(2);

            GlSummaryResponse gl2001 = summary.stream()
                .filter(s -> "2001".equals(s.glAccountCode))
                .findFirst().orElseThrow();
            assertThat(gl2001.totalDebits).isEqualByComparingTo("1000.00");
            assertThat(gl2001.totalCredits).isEqualByComparingTo("1000.00");
            assertThat(gl2001.netMovement).isEqualByComparingTo("0.00");
            assertThat(gl2001.entryCount).isEqualTo(2);

            GlSummaryResponse gl4002 = summary.stream()
                .filter(s -> "4002".equals(s.glAccountCode))
                .findFirst().orElseThrow();
            assertThat(gl4002.totalCredits).isEqualByComparingTo("500.00");
            assertThat(gl4002.totalDebits).isEqualByComparingTo("0.00");
            assertThat(gl4002.netMovement).isEqualByComparingTo("500.00");
        }
    }
}