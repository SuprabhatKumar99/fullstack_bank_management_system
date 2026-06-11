package com.cbs.account_service.service;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.cache.annotation.Caching;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cbs.account_service.config.RedisConfig;
import com.cbs.account_service.dto.request.CloseAccountRequest;
import com.cbs.account_service.dto.request.OpenAccountRequest;
import com.cbs.account_service.dto.request.UpdateAccountRequest;
import com.cbs.account_service.dto.response.AccountResponse;
import com.cbs.account_service.dto.response.AccountSummaryResponse;
import com.cbs.account_service.dto.response.BalanceResponse;
import com.cbs.account_service.entity.Account;
import com.cbs.account_service.enums.AccountStatus;
import com.cbs.account_service.event.AccountClosedEvent;
import com.cbs.account_service.event.AccountFrozenEvent;
import com.cbs.account_service.event.AccountOpenedEvent;
import com.cbs.account_service.exception.AccountNotFoundException;
import com.cbs.account_service.exception.AccountOperationException;
import com.cbs.account_service.exception.InsufficientFundsException;
import com.cbs.account_service.mapper.AccountMapper;
import com.cbs.account_service.repository.AccountRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class AccountService {

    private final AccountRepository accountRepository;
    private final AccountMapper     accountMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // ── Business rule defaults (from application.yml) ─────────────
    @Value("${account.rules.savings.minimum-balance:1000.00}")
    private BigDecimal savingsMinBalance;

    @Value("${account.rules.current.minimum-balance:5000.00}")
    private BigDecimal currentMinBalance;

    @Value("${account.rules.fd.min-deposit:10000.00}")
    private BigDecimal fdMinDeposit;

    @Value("${account.rules.rd.min-monthly-instalment:500.00}")
    private BigDecimal rdMinInstalment;

    // ── Kafka topics ──────────────────────────────────────────────
    @Value("${kafka.topics.account-opened}")
    private String topicAccountOpened;

    @Value("${kafka.topics.account-closed}")
    private String topicAccountClosed;

    @Value("${kafka.topics.account-frozen}")
    private String topicAccountFrozen;

    // ─────────────────────────────────────────────────────────────
    // 1. OPEN ACCOUNT
    // ─────────────────────────────────────────────────────────────

    /**
     * Opens a new bank account.
     *
     * Flow:
     *  1. Validate cross-field rules per account type.
     *  2. Map request → entity, apply type-specific defaults.
     *  3. Persist (DB sequence generates accountNumber).
     *  4. Activate immediately (SAVINGS/CURRENT) or leave PENDING (FD/RD/LOAN).
     *  5. Publish AccountOpenedEvent to Kafka.
     *
     * Cache: evict customer-accounts list on open.
     */
    @Transactional
    @CacheEvict(value = RedisConfig.CACHE_CUSTOMER_ACCOUNTS, key = "#request.customerId")
    public AccountResponse openAccount(OpenAccountRequest request, UUID createdByStaffId) {
        validateOpenRequest(request);

        Account account = accountMapper.toEntity(request);
        account.setCreatedBy(createdByStaffId);

        applyTypeDefaults(account, request);

        Account saved = accountRepository.save(account);

        // Activate deposit accounts immediately;
        // FD/RD/LOAN stay PENDING_ACTIVATION until funding is confirmed.
        if (saved.getAccountType().isDepositAccount()) {
            saved.activate();
            saved = accountRepository.save(saved);
        }

        log.info("Account opened: id={} number={} type={} customer={}",
            saved.getAccountId(), saved.getAccountNumber(),
            saved.getAccountType(), saved.getCustomerId());

        kafkaTemplate.send(topicAccountOpened, saved.getAccountId().toString(),
            AccountOpenedEvent.builder()
                .accountId(saved.getAccountId())
                .accountNumber(saved.getAccountNumber())
                .customerId(saved.getCustomerId())
                .branchId(saved.getBranchId())
                .accountType(saved.getAccountType())
                .currency(saved.getCurrency())
                .minimumBalance(saved.getMinimumBalance())
                .occurredAt(OffsetDateTime.now())
                .build());

        return accountMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────
    // 2. READ — SINGLE ACCOUNT
    // ─────────────────────────────────────────────────────────────

    /**
     * Full account detail. Cached in Redis for 10 minutes.
     * Cache key: account-detail::{accountId}
     */
    @Transactional(readOnly = true)
    @Cacheable(value = RedisConfig.CACHE_ACCOUNT_DETAIL, key = "#accountId")
    public AccountResponse getById(UUID accountId) {
        return accountMapper.toResponse(findOrThrow(accountId));
    }

    @Transactional(readOnly = true)
    @Cacheable(value = RedisConfig.CACHE_ACCOUNT_DETAIL, key = "#accountNumber")
    public AccountResponse getByAccountNumber(String accountNumber) {
        Account account = accountRepository.findByAccountNumber(accountNumber)
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + accountNumber));
        return accountMapper.toResponse(account);
    }

    // ─────────────────────────────────────────────────────────────
    // 3. READ — BALANCE (hot path — always from cache)
    // ─────────────────────────────────────────────────────────────

    /**
     * Balance enquiry — served from Redis cache.
     * Cache key: account-balance::{accountId}
     * TTL: 5 minutes.
     *
     * Cache miss path: reads from PostgreSQL, populates cache, returns.
     * Cache hit  path: returns Redis entry directly (sub-millisecond).
     *
     * The Transaction Service calls evictBalance() after every transaction
     * to ensure the cache is always fresh after writes.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = RedisConfig.CACHE_BALANCE, key = "#accountId")
    public BalanceResponse getBalance(UUID accountId) {
        Account account = findOrThrow(accountId);
        if (!account.getStatus().allowsRead()) {
            throw new AccountOperationException(
                "Account " + accountId + " is CLOSED and has no accessible balance.");
        }
        return accountMapper.toBalanceResponse(account);
    }

    /**
     * Evicts the balance cache for an account.
     * Called by the Transaction Service after every ledger write.
     * Also called internally after freeze/unfreeze/close operations.
     */
    @Caching(evict = {
        @CacheEvict(value = RedisConfig.CACHE_BALANCE,        key = "#accountId"),
        @CacheEvict(value = RedisConfig.CACHE_ACCOUNT_DETAIL, key = "#accountId")
    })
    public void evictAccountCache(UUID accountId) {
        log.debug("Cache evicted for account: {}", accountId);
    }

    // ─────────────────────────────────────────────────────────────
    // 4. READ — LIST / SEARCH
    // ─────────────────────────────────────────────────────────────

    /**
     * All accounts for a customer — used on the teller dashboard.
     * Cached; evicted when any account for the customer changes.
     */
    @Transactional(readOnly = true)
    @Cacheable(value = RedisConfig.CACHE_CUSTOMER_ACCOUNTS, key = "#customerId")
    public List<AccountSummaryResponse> getAccountsByCustomer(UUID customerId) {
        return accountRepository.findByCustomerId(customerId)
            .stream()
            .map(accountMapper::toSummary)
            .toList();
    }

    @Transactional(readOnly = true)
    public Page<AccountSummaryResponse> getAccountsByBranch(UUID branchId, Pageable pageable) {
        return accountRepository.findByBranchId(branchId, pageable)
            .map(accountMapper::toSummary);
    }

    @Transactional(readOnly = true)
    public Page<AccountSummaryResponse> getAccountsByStatus(AccountStatus status,
                                                            Pageable pageable) {
        return accountRepository.findByStatus(status, pageable)
            .map(accountMapper::toSummary);
    }

    // ─────────────────────────────────────────────────────────────
    // 5. UPDATE ACCOUNT
    // ─────────────────────────────────────────────────────────────

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = RedisConfig.CACHE_ACCOUNT_DETAIL,  key = "#accountId"),
        @CacheEvict(value = RedisConfig.CACHE_BALANCE,         key = "#accountId")
    })
    public AccountResponse updateAccount(UUID accountId, UpdateAccountRequest request) {
        Account account = findOrThrow(accountId);

        // Validate overdraft limit change
        if (request.getOverdraftLimit() != null
                && !account.getAccountType().allowsOverdraft()) {
            throw new AccountOperationException(
                "Overdraft limit can only be set on CURRENT or OVERDRAFT accounts.");
        }

        accountMapper.updateFromRequest(request, account);
        Account updated = accountRepository.save(account);

        log.info("Account updated: id={}", accountId);
        return accountMapper.toResponse(updated);
    }

    // ─────────────────────────────────────────────────────────────
    // 6. ACCOUNT LIFECYCLE — ACTIVATE
    // ─────────────────────────────────────────────────────────────

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = RedisConfig.CACHE_ACCOUNT_DETAIL,   key = "#accountId"),
        @CacheEvict(value = RedisConfig.CACHE_BALANCE,          key = "#accountId"),
        @CacheEvict(value = RedisConfig.CACHE_CUSTOMER_ACCOUNTS,key = "#result.customerId")
    })
    public AccountResponse activateAccount(UUID accountId) {
        Account account = findOrThrow(accountId);
        account.activate();
        Account saved = accountRepository.save(account);
        log.info("Account activated: id={} number={}", accountId, saved.getAccountNumber());
        return accountMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────
    // 7. FREEZE / UNFREEZE
    // ─────────────────────────────────────────────────────────────

    /**
     * Quick-freeze — sets is_frozen=true without changing the status.
     * Used by the Fraud Detection Service for immediate holds.
     * Can be reversed instantly via unfreezeAccount().
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = RedisConfig.CACHE_ACCOUNT_DETAIL, key = "#accountId"),
        @CacheEvict(value = RedisConfig.CACHE_BALANCE,        key = "#accountId")
    })
    public AccountResponse freezeAccount(UUID accountId, String reason) {
        Account account = findOrThrow(accountId);
        account.quickFreeze();
        Account saved = accountRepository.save(account);

        log.info("Account quick-frozen: id={} reason={}", accountId, reason);

        kafkaTemplate.send(topicAccountFrozen, accountId.toString(),
            AccountFrozenEvent.builder()
                .accountId(accountId)
                .accountNumber(saved.getAccountNumber())
                .customerId(saved.getCustomerId())
                .frozen(true)
                .reason(reason)
                .occurredAt(OffsetDateTime.now())
                .build());

        return accountMapper.toResponse(saved);
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = RedisConfig.CACHE_ACCOUNT_DETAIL, key = "#accountId"),
        @CacheEvict(value = RedisConfig.CACHE_BALANCE,        key = "#accountId")
    })
    public AccountResponse unfreezeAccount(UUID accountId) {
        Account account = findOrThrow(accountId);
        account.quickUnfreeze();
        Account saved = accountRepository.save(account);

        log.info("Account unfrozen: id={}", accountId);

        kafkaTemplate.send(topicAccountFrozen, accountId.toString(),
            AccountFrozenEvent.builder()
                .accountId(accountId)
                .accountNumber(saved.getAccountNumber())
                .customerId(saved.getCustomerId())
                .frozen(false)
                .reason("Manually unfrozen")
                .occurredAt(OffsetDateTime.now())
                .build());

        return accountMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────
    // 8. CLOSE ACCOUNT
    // ─────────────────────────────────────────────────────────────

    /**
     * Closes an account.
     *
     * Pre-conditions enforced by Account.close():
     * - Balance must be exactly zero (no outstanding funds).
     * - Account must not already be CLOSED.
     *
     * After closure:
     * - Evict all related caches.
     * - Publish AccountClosedEvent (Card Service blocks linked cards).
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = RedisConfig.CACHE_ACCOUNT_DETAIL,   key = "#accountId"),
        @CacheEvict(value = RedisConfig.CACHE_BALANCE,          key = "#accountId"),
        @CacheEvict(value = RedisConfig.CACHE_CUSTOMER_ACCOUNTS,key = "#result.customerId")
    })
    public AccountResponse closeAccount(UUID accountId, CloseAccountRequest request) {
        Account account = findOrThrow(accountId);

        // domain method enforces zero-balance and non-closed preconditions
        account.close(request.getReason());
        Account saved = accountRepository.save(account);

        log.info("Account closed: id={} number={} reason={}",
            accountId, saved.getAccountNumber(), request.getReason());

        kafkaTemplate.send(topicAccountClosed, accountId.toString(),
            AccountClosedEvent.builder()
                .accountId(accountId)
                .accountNumber(saved.getAccountNumber())
                .customerId(saved.getCustomerId())
                .closureReason(request.getReason())
                .occurredAt(OffsetDateTime.now())
                .build());

        return accountMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────
    // 9. BALANCE AGGREGATES
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public BigDecimal getTotalBalanceByCustomer(UUID customerId) {
        return accountRepository.sumActiveBalancesByCustomer(customerId);
    }

    @Transactional(readOnly = true)
    public BigDecimal getTotalDepositsByBranch(UUID branchId) {
        return accountRepository.sumDepositsByBranch(branchId);
    }

    // ─────────────────────────────────────────────────────────────
    // 10. INTERNAL — called by Transaction Service via internal API
    // ─────────────────────────────────────────────────────────────

    /**
     * Validates that an account can debit the given amount.
     * Called by the Transaction Service BEFORE writing ledger entries.
     * Does NOT modify the balance — that is done by the ledger trigger.
     *
     * Uses pessimistic write lock to prevent race conditions on
     * high-frequency accounts (e.g. current accounts with many concurrent txns).
     */
    @Transactional
    public void validateDebit(UUID accountId, BigDecimal amount) {
        Account account = accountRepository.findByIdForUpdate(accountId)
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found for debit validation: " + accountId));

        if (!account.isOperational()) {
            throw new AccountOperationException(
                "Account " + accountId + " is not operational. Status: "
                    + account.getStatus() + ", frozen: " + account.getIsFrozen());
        }

        if (!account.canDebit(amount)) {
            throw new InsufficientFundsException(
                accountId, amount, account.getAvailableBalance());
        }
    }

    /**
     * Places a hold on funds (e.g. for UPI mandate, cheque clearing).
     * Evicts balance cache — available balance has changed.
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = RedisConfig.CACHE_BALANCE,        key = "#accountId"),
        @CacheEvict(value = RedisConfig.CACHE_ACCOUNT_DETAIL, key = "#accountId")
    })
    public void placeHold(UUID accountId, BigDecimal amount) {
        Account account = findOrThrow(accountId);
        account.placeHold(amount);
        accountRepository.save(account);
        log.info("Hold placed: account={} amount={}", accountId, amount);
    }

    /**
     * Releases a previously placed hold.
     */
    @Transactional
    @Caching(evict = {
        @CacheEvict(value = RedisConfig.CACHE_BALANCE,        key = "#accountId"),
        @CacheEvict(value = RedisConfig.CACHE_ACCOUNT_DETAIL, key = "#accountId")
    })
    public void releaseHold(UUID accountId, BigDecimal amount) {
        Account account = findOrThrow(accountId);
        account.releaseHold(amount);
        accountRepository.save(account);
        log.info("Hold released: account={} amount={}", accountId, amount);
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────

    private Account findOrThrow(UUID accountId) {
        return accountRepository.findById(accountId)
            .orElseThrow(() -> new AccountNotFoundException(
                "Account not found: " + accountId));
    }

    /**
     * Applies type-specific defaults to the account entity after mapping.
     * Centralises all business rules for each account type in one place.
     */
    private void applyTypeDefaults(Account account, OpenAccountRequest request) {
        switch (account.getAccountType()) {
            case SAVINGS -> {
                account.setMinimumBalance(savingsMinBalance);
                account.setInterestRate(new BigDecimal("3.50")); // default savings rate
                account.setOverdraftLimit(BigDecimal.ZERO);
            }
            case CURRENT -> {
                account.setMinimumBalance(currentMinBalance);
                account.setInterestRate(BigDecimal.ZERO);
                // Apply requested OD limit, defaulting to zero
                account.setOverdraftLimit(
                    request.getOverdraftLimit() != null
                        ? request.getOverdraftLimit()
                        : BigDecimal.ZERO);
            }
            case OVERDRAFT -> {
                account.setMinimumBalance(currentMinBalance);
                account.setOverdraftLimit(
                    request.getOverdraftLimit() != null
                        ? request.getOverdraftLimit()
                        : new BigDecimal("50000.00")); // default OD limit
            }
            case FIXED_DEPOSIT -> {
                if (request.getInitialDeposit() == null
                        || request.getInitialDeposit().compareTo(fdMinDeposit) < 0) {
                    throw new AccountOperationException(
                        "FD requires a minimum deposit of ₹" + fdMinDeposit);
                }
                if (request.getTenureMonths() == null) {
                    throw new AccountOperationException(
                        "Tenure in months is required for Fixed Deposit.");
                }
                account.setMinimumBalance(request.getInitialDeposit());
                account.setMaturityDate(
                    LocalDate.now().plusMonths(request.getTenureMonths()));
                account.setInterestRate(resolveFdRate(request.getTenureMonths()));
            }
            case RECURRING_DEPOSIT -> {
                if (request.getInitialDeposit() == null
                        || request.getInitialDeposit().compareTo(rdMinInstalment) < 0) {
                    throw new AccountOperationException(
                        "RD monthly instalment must be at least ₹" + rdMinInstalment);
                }
                if (request.getTenureMonths() == null) {
                    throw new AccountOperationException(
                        "Tenure in months is required for Recurring Deposit.");
                }
                account.setMinimumBalance(request.getInitialDeposit());
                account.setMaturityDate(
                    LocalDate.now().plusMonths(request.getTenureMonths()));
                account.setInterestRate(new BigDecimal("6.50")); // standard RD rate
            }
            case LOAN -> {
                account.setOverdraftLimit(
                    request.getInitialDeposit() != null
                        ? request.getInitialDeposit()
                        : BigDecimal.ZERO);
                account.setMinimumBalance(BigDecimal.ZERO);
            }
        }
    }

    /**
     * RBI-style FD interest rate slab.
     * In production this would be fetched from a rate_cards table.
     */
    private BigDecimal resolveFdRate(int tenureMonths) {
        if (tenureMonths < 3)  return new BigDecimal("3.50");
        if (tenureMonths < 6)  return new BigDecimal("5.00");
        if (tenureMonths < 12) return new BigDecimal("6.00");
        if (tenureMonths < 24) return new BigDecimal("6.80");
        if (tenureMonths < 60) return new BigDecimal("7.00");
        return new BigDecimal("6.50"); // > 5 years
    }

    /**
     * Cross-field validation for OpenAccountRequest.
     */
    private void validateOpenRequest(OpenAccountRequest request) {
        if (request.getAccountType().isTermDeposit()
                && request.getParentAccountId() == null) {
            throw new AccountOperationException(
                "FD/RD accounts must be linked to a parent savings or current account.");
        }
        if (request.getAccountType().isTermDeposit()
                && !accountRepository.existsById(request.getParentAccountId())) {
            throw new AccountNotFoundException(
                "Parent account not found: " + request.getParentAccountId());
        }
    }
}