package com.cbs.ledger_service.controller;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cbs.ledger_service.dto.response.BalanceAuditResponse;
import com.cbs.ledger_service.dto.response.GlSummaryResponse;
import com.cbs.ledger_service.dto.response.IntegrityViolationResponse;
import com.cbs.ledger_service.dto.response.StatementEntryResponse;
import com.cbs.ledger_service.service.LedgerService;

import lombok.RequiredArgsConstructor;

/**
 * REST controller for Ledger Service.
 *
 * Base URL : /api/v1/ledger
 * Port     : 8084
 *
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │ GET  /statement/{accountId}          Paginated passbook statement        │
 * │ GET  /transaction/{transactionId}    All ledger legs for one transaction │
 * │ GET  /balance-audit/{accountId}      Recompute balance from ledger       │
 * │ GET  /gl-summary?date=               Daily GL summary (accounting)       │
 * │ GET  /integrity?date=                Double-entry violation check        │
 * └──────────────────────────────────────────────────────────────────────────┘
 */
@RestController
@RequestMapping("/api/v1/ledger")
@RequiredArgsConstructor
public class LedgerController {

    private final LedgerService ledgerService;

    /**
     * GET /api/v1/ledger/statement/{accountId}
     *   ?from=2024-01-01&to=2024-03-31&page=0&size=20
     *
     * Passbook-style statement with separate debit/credit columns
     * and running balance (balanceAfter) per entry.
     */
    @GetMapping("/statement/{accountId}")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN') or " +
                  "#accountId.toString() == authentication.name")
    public ResponseEntity<Page<StatementEntryResponse>> getStatement(
            @PathVariable UUID accountId,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(
            ledgerService.getStatement(accountId, from, to, pageable));
    }

    /**
     * GET /api/v1/ledger/transaction/{transactionId}
     *
     * All ledger entries for one transaction (typically 2 rows — one debit, one credit).
     * Used for transaction detail view and receipt generation.
     */
    @GetMapping("/transaction/{transactionId}")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN', 'PAYMENT_SERVICE')")
    public ResponseEntity<List<StatementEntryResponse>> getEntriesByTransaction(
            @PathVariable UUID transactionId) {

        return ResponseEntity.ok(
            ledgerService.getEntriesByTransaction(transactionId));
    }

    /**
     * GET /api/v1/ledger/balance-audit/{accountId}?cachedBalance=10000.00
     *
     * Recomputes the ledger balance from scratch and compares it to
     * the cached value in accounts.balance.
     * Returns { isBalanced: true, discrepancy: 0.00 } when healthy.
     * Any non-zero discrepancy must be immediately escalated.
     */
    @GetMapping("/balance-audit/{accountId}")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<BalanceAuditResponse> auditBalance(
            @PathVariable UUID accountId,
            @RequestParam BigDecimal cachedBalance) {

        return ResponseEntity.ok(
            ledgerService.auditBalance(accountId, cachedBalance));
    }

    /**
     * GET /api/v1/ledger/gl-summary?date=2024-01-15
     *
     * Daily General Ledger summary grouped by GL account code.
     * Used by the accounting team for trial balance and RBI reports.
     */
    @GetMapping("/gl-summary")
    @PreAuthorize("hasAnyRole('ADMIN', 'AUDITOR')")
    public ResponseEntity<List<GlSummaryResponse>> getDailySummary(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                LocalDate date) {

        return ResponseEntity.ok(ledgerService.getDailySummary(date));
    }

    /**
     * GET /api/v1/ledger/integrity?date=2024-01-15
     *
     * Double-entry integrity check for a given date.
     * Returns an empty list in a healthy system.
     * Any non-empty result is a CRITICAL financial integrity violation.
     */
    @GetMapping("/integrity")
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<List<IntegrityViolationResponse>> checkIntegrity(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE)
                LocalDate date) {

        List<IntegrityViolationResponse> violations =
            ledgerService.findIntegrityViolations(date);

        if (!violations.isEmpty()) {
            // In production: trigger an alert / PagerDuty page here
        }

        return ResponseEntity.ok(violations);
    }
}