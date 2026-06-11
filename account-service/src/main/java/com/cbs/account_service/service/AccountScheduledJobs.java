package com.cbs.account_service.service;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import com.cbs.account_service.entity.Account;
import com.cbs.account_service.repository.AccountRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Nightly scheduled jobs for Account Service.
 *
 * Jobs:
 * ┌────────────────────────────────────────────────────────────────────────┐
 * │ 00:10 IST  Dormancy detection — mark ACTIVE accounts as DORMANT       │
 * │            if inactive for > 2 years (RBI regulation)                 │
 * │                                                                        │
 * │ 00:30 IST  FD/RD maturity — process accounts maturing today,          │
 * │            credit interest to parent account, close deposit account   │
 * └────────────────────────────────────────────────────────────────────────┘
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class AccountScheduledJobs {

    private final AccountRepository accountRepository;
    private final AccountService    accountService;

    // RBI regulation: account becomes dormant after 2 years of no transactions
    private static final int DORMANCY_YEARS = 2;

    // ── Dormancy detection ────────────────────────────────────────

    @Scheduled(cron = "0 10 0 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void detectDormantAccounts() {
        log.info("Dormancy detection job started");

        OffsetDateTime cutoff = OffsetDateTime.now().minusYears(DORMANCY_YEARS);
        List<Account> candidates = accountRepository.findPotentiallyDormant(cutoff);

        int count = 0;
        for (Account account : candidates) {
            try {
                account.markDormant();
                accountRepository.save(account);
                // Evict cache so next balance read reflects DORMANT status
                accountService.evictAccountCache(account.getAccountId());
                count++;
            } catch (Exception ex) {
                log.error("Failed to mark account {} as dormant: {}",
                    account.getAccountId(), ex.getMessage());
            }
        }

        log.info("Dormancy detection job completed: {} accounts marked DORMANT", count);
    }

    // ── FD / RD maturity processing ───────────────────────────────

    /**
     * Processes Fixed Deposits and Recurring Deposits that mature today.
     *
     * For each maturing FD/RD:
     * 1. Calculate maturity amount (principal + interest).
     * 2. Credit maturity amount to the parent savings/current account.
     *    NOTE: In production this goes through the Transaction Service to
     *    generate proper ledger entries. Here we stub the credit.
     * 3. Close the FD/RD account.
     *
     * In production, step 2 would POST to /api/v1/transactions with
     * type=INTEREST_CREDIT and the parent account as destination.
     */
    @Scheduled(cron = "0 30 0 * * *", zone = "Asia/Kolkata")
    @Transactional
    public void processMaturingDeposits() {
        log.info("FD/RD maturity processing job started");

        List<Account> maturing = accountRepository.findMaturingToday(LocalDate.now());
        int processed = 0;

        for (Account account : maturing) {
            try {
                log.info("Processing maturity for account: {} type: {} parent: {}",
                    account.getAccountNumber(),
                    account.getAccountType(),
                    account.getParentAccountId());

                // TODO: Call Transaction Service to credit maturity amount
                // transactionServiceClient.creditMaturity(account);

                // Close the term deposit account
                account.close("Matured on " + LocalDate.now());
                accountRepository.save(account);
                accountService.evictAccountCache(account.getAccountId());

                // Also evict parent account cache (its balance will change)
                if (account.getParentAccountId() != null) {
                    accountService.evictAccountCache(account.getParentAccountId());
                }

                processed++;
            } catch (Exception ex) {
                log.error("Failed to process maturity for account {}: {}",
                    account.getAccountId(), ex.getMessage());
            }
        }

        log.info("FD/RD maturity job completed: {} accounts processed", processed);
    }
}