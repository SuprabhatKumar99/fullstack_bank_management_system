package com.cbs.ledger_service.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.stream.Collectors;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cbs.ledger_service.dto.response.BalanceAuditResponse;
import com.cbs.ledger_service.dto.response.GlSummaryResponse;
import com.cbs.ledger_service.dto.response.IntegrityViolationResponse;
import com.cbs.ledger_service.dto.response.StatementEntryResponse;
import com.cbs.ledger_service.entity.LedgerEntry;
import com.cbs.ledger_service.repository.LedgerEntryRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Ledger Service — read-only query layer over ledger_entries.
 *
 * Responsibilities:
 * ─────────────────
 * 1. Account statement:  all entries for an account with date range filter.
 * 2. Balance audit:      recompute balance from ledger, compare to cached value.
 * 3. GL summary:         aggregate debits/credits by GL account code (for RBI reporting).
 * 4. Integrity check:    find any transaction where debits ≠ credits.
 * 5. Transaction detail: all ledger legs for one transaction.
 *
 * This service NEVER writes. All writes go through the Transaction Service.
 * Read replicas can be pointed here to offload the primary DB.
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)   // ALL methods are read-only
public class LedgerService {

    private final LedgerEntryRepository ledgerEntryRepository;

    // ─────────────────────────────────────────────────────────────
    // 1. ACCOUNT STATEMENT
    // ─────────────────────────────────────────────────────────────

    /**
     * Paginated statement for one account with date range.
     * Returns entries in reverse chronological order (most recent first).
     *
     * Each entry carries balanceAfter — the running balance at that point —
     * so the statement renders like a classic bank passbook without any
     * cumulative SUM() computation.
     */
    public Page<StatementEntryResponse> getStatement(UUID accountId,
                                                      LocalDate from,
                                                      LocalDate to,
                                                      Pageable pageable) {
        return ledgerEntryRepository
            .findByAccountIdAndValueDateBetweenOrderByCreatedAtDesc(
                accountId, from, to, pageable)
            .map(this::toStatementEntry);
    }

    // ─────────────────────────────────────────────────────────────
    // 2. BALANCE AUDIT
    // ─────────────────────────────────────────────────────────────

    /**
     * Recomputes balance from the ledger from scratch.
     *
     * Formula: ledgerBalance = SUM(CREDIT) - SUM(DEBIT) for all entries.
     *
     * Used by:
     * - Daily reconciliation job
     * - Teller UI "verify balance" button
     * - Automated integrity monitoring
     *
     * If ledgerBalance ≠ accounts.balance → discrepancy must be investigated.
     * In a correctly functioning system this should ALWAYS return zero discrepancy.
     */
    public BalanceAuditResponse auditBalance(UUID accountId, BigDecimal cachedBalance) {
        BigDecimal ledgerBalance =
            ledgerEntryRepository.computeLedgerBalance(accountId);

        BigDecimal discrepancy = cachedBalance.subtract(ledgerBalance);
        boolean isBalanced     = discrepancy.compareTo(BigDecimal.ZERO) == 0;

        if (!isBalanced) {
            log.error("BALANCE DISCREPANCY DETECTED: account={} cached={} ledger={} diff={}",
                accountId, cachedBalance, ledgerBalance, discrepancy);
        }

        return BalanceAuditResponse.builder()
            .accountId(accountId)
            .cachedBalance(cachedBalance)
            .ledgerBalance(ledgerBalance)
            .discrepancy(discrepancy)
            .isBalanced(isBalanced)
            .auditedAt(java.time.OffsetDateTime.now())
            .build();
    }

    // ─────────────────────────────────────────────────────────────
    // 3. GL SUMMARY (for regulatory / accounting reports)
    // ─────────────────────────────────────────────────────────────

    /**
     * Daily GL summary — aggregate debits and credits by GL account code.
     *
     * Used by:
     * - Accounting team (trial balance)
     * - RBI statutory reports (Form X, Balance Sheet)
     * - Internal auditors
     *
     * Returns one row per GL code with total debits, total credits, net movement.
     * Net movement positive = net credit (liabilities/revenue increased).
     * Net movement negative = net debit (assets/expenses increased).
     */
    public List<GlSummaryResponse> getDailySummary(LocalDate date) {
        List<LedgerEntry> entries =
            ledgerEntryRepository.findByValueDate(date);

        Map<String, List<LedgerEntry>> grouped = entries.stream()
            .collect(Collectors.groupingBy(e ->
                e.getGlAccountCode() != null ? e.getGlAccountCode() : "UNCLASSIFIED"));

        return grouped.entrySet().stream()
            .map(entry -> {
                List<LedgerEntry> glEntries = entry.getValue();
                BigDecimal debits  = glEntries.stream()
                    .filter(e -> "DEBIT".equals(e.getEntryType()))
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal credits = glEntries.stream()
                    .filter(e -> "CREDIT".equals(e.getEntryType()))
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);

                return GlSummaryResponse.builder()
                    .glAccountCode(entry.getKey())
                    .accountType(glEntries.get(0).getAccountType())
                    .date(date)
                    .totalDebits(debits)
                    .totalCredits(credits)
                    .netMovement(credits.subtract(debits))
                    .entryCount(glEntries.size())
                    .build();
            })
            .sorted(java.util.Comparator.comparing(GlSummaryResponse::getGlAccountCode))
            .toList();
    }

    // ─────────────────────────────────────────────────────────────
    // 4. INTEGRITY CHECK
    // ─────────────────────────────────────────────────────────────

    /**
     * Finds all transactions where SUM(DEBIT) ≠ SUM(CREDIT).
     *
     * In a correctly functioning system this ALWAYS returns an empty list.
     * Any non-empty result is a critical financial integrity violation requiring
     * immediate investigation and likely a hotfix + reversal.
     *
     * Run nightly by the reconciliation job.
     */
    public List<IntegrityViolationResponse> findIntegrityViolations(LocalDate date) {
        List<LedgerEntry> entries =
            ledgerEntryRepository.findByValueDate(date);

        return entries.stream()
            .collect(Collectors.groupingBy(LedgerEntry::getTransactionId))
            .entrySet().stream()
            .filter(entry -> {
                List<LedgerEntry> txnEntries = entry.getValue();
                BigDecimal debits  = txnEntries.stream()
                    .filter(e -> "DEBIT".equals(e.getEntryType()))
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal credits = txnEntries.stream()
                    .filter(e -> "CREDIT".equals(e.getEntryType()))
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                return debits.compareTo(credits) != 0;
            })
            .map(entry -> {
                List<LedgerEntry> txnEntries = entry.getValue();
                BigDecimal debits  = txnEntries.stream()
                    .filter(e -> "DEBIT".equals(e.getEntryType()))
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                BigDecimal credits = txnEntries.stream()
                    .filter(e -> "CREDIT".equals(e.getEntryType()))
                    .map(LedgerEntry::getAmount)
                    .reduce(BigDecimal.ZERO, BigDecimal::add);
                return IntegrityViolationResponse.builder()
                    .transactionId(entry.getKey())
                    .totalDebits(debits)
                    .totalCredits(credits)
                    .imbalance(credits.subtract(debits))
                    .build();
            })
            .toList();
    }

    // ─────────────────────────────────────────────────────────────
    // 5. TRANSACTION DETAIL
    // ─────────────────────────────────────────────────────────────

    public List<StatementEntryResponse> getEntriesByTransaction(UUID transactionId) {
        return ledgerEntryRepository.findByTransactionId(transactionId)
            .stream()
            .map(this::toStatementEntry)
            .toList();
    }

    // ─────────────────────────────────────────────────────────────
    // MAPPER
    // ─────────────────────────────────────────────────────────────

    private StatementEntryResponse toStatementEntry(LedgerEntry e) {
        return StatementEntryResponse.builder()
            .entryId(e.getEntryId())
            .transactionId(e.getTransactionId())
            .accountId(e.getAccountId())
            .entryType(e.getEntryType())
            .debitAmount("DEBIT".equals(e.getEntryType()) ? e.getAmount() : null)
            .creditAmount("CREDIT".equals(e.getEntryType()) ? e.getAmount() : null)
            .balanceAfter(e.getBalanceAfter())
            .currency(e.getCurrency())
            .glAccountCode(e.getGlAccountCode())
            .narration(e.getNarration())
            .valueDate(e.getValueDate())
            .createdAt(e.getCreatedAt())
            .build();
    }
}