package com.cbs.ledger_service.service;


import java.time.LocalDate;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import com.cbs.ledger_service.dto.response.IntegrityViolationResponse;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Nightly reconciliation job.
 *
 * Runs at 01:00 IST every night.
 * Scans yesterday's ledger entries for double-entry violations.
 * Logs CRITICAL alert if any imbalance is found.
 *
 * In production:
 * - Send alert to PagerDuty / OpsGenie on violation.
 * - Publish to Kafka topic: ledger.integrity.violation.
 * - Freeze further transaction processing until resolved.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class ReconciliationJob {

    private final LedgerService ledgerService;

    @Scheduled(cron = "0 0 1 * * *", zone = "Asia/Kolkata")
    public void runNightlyIntegrityCheck() {
        LocalDate yesterday = LocalDate.now().minusDays(1);
        log.info("Nightly integrity check started for date: {}", yesterday);

        try {
            List<IntegrityViolationResponse> violations =
                ledgerService.findIntegrityViolations(yesterday);

            if (violations.isEmpty()) {
                log.info("✅ Integrity check PASSED for {} — all transactions balanced.",
                    yesterday);
            } else {
                violations.forEach(v ->
                    log.error("🚨 CRITICAL INTEGRITY VIOLATION: " +
                        "transactionId={} debits={} credits={} imbalance={}",
                        v.transactionId, v.totalDebits,
                        v.totalCredits, v.imbalance));

                log.error("TOTAL VIOLATIONS: {} — IMMEDIATE INVESTIGATION REQUIRED!",
                    violations.size());
                // TODO: alert(violations) → PagerDuty / Kafka
            }

            // GL summary log
            List<?> glSummary = ledgerService.getDailySummary(yesterday);
            log.info("GL summary for {}: {} codes processed", yesterday, glSummary.size());

        } catch (Exception ex) {
            log.error("Nightly integrity check FAILED with exception", ex);
        }
    }
}