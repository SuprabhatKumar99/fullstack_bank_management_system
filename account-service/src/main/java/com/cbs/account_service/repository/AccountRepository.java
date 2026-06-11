package com.cbs.account_service.repository;


import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cbs.account_service.entity.Account;
import com.cbs.account_service.enums.AccountStatus;
import com.cbs.account_service.enums.AccountType;

import jakarta.persistence.LockModeType;

@Repository
public interface AccountRepository extends JpaRepository<Account, UUID> {

    // ── Lookup by account number ──────────────────────────────────
    Optional<Account> findByAccountNumber(String accountNumber);

    boolean existsByAccountNumber(String accountNumber);

    // ── Pessimistic write lock — used by Transaction Service ──────
    /**
     * Acquires a SELECT FOR UPDATE lock on the account row.
     * Used by the Transaction Service before debiting to prevent
     * two concurrent transactions from over-drawing the account.
     *
     * Note: Optimistic locking (@Version) handles most cases.
     * Pessimistic lock is used for high-value / ATM transactions
     * where retrying is unacceptable.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Account a WHERE a.accountId = :id")
    Optional<Account> findByIdForUpdate(@Param("id") UUID id);

    // ── Customer account listing ──────────────────────────────────
    List<Account> findByCustomerId(UUID customerId);

    Page<Account> findByCustomerIdAndStatus(UUID customerId,
                                            AccountStatus status,
                                            Pageable pageable);

    // ── Branch-level listing ──────────────────────────────────────
    Page<Account> findByBranchId(UUID branchId, Pageable pageable);

    // ── Status filters ────────────────────────────────────────────
    Page<Account> findByStatus(AccountStatus status, Pageable pageable);

    // ── Dormancy detection (nightly job) ─────────────────────────
    /**
     * Accounts that have been ACTIVE but had no transactions since
     * the dormancy cutoff (RBI: 2 years of inactivity).
     * The actual last-transaction date lives in the transactions table,
     * so this query joins across services — use a denormalized
     * last_transaction_date column here in practice.
     */
    @Query("""
        SELECT a FROM Account a
        WHERE a.status = 'ACTIVE'
          AND a.dormantSince IS NULL
          AND a.activatedAt < :cutoffDate
        """)
    List<Account> findPotentiallyDormant(@Param("cutoffDate") OffsetDateTime cutoffDate);

    // ── FD / RD maturity scheduler ────────────────────────────────
    @Query("""
        SELECT a FROM Account a
        WHERE a.accountType IN ('FIXED_DEPOSIT', 'RECURRING_DEPOSIT')
          AND a.status = 'ACTIVE'
          AND a.maturityDate <= :today
        """)
    List<Account> findMaturingToday(@Param("today") LocalDate today);

    // ── Balance queries ───────────────────────────────────────────

    /** Total balance across all ACTIVE accounts of a customer. */
    @Query("""
        SELECT COALESCE(SUM(a.balance), 0)
        FROM Account a
        WHERE a.customerId = :customerId
          AND a.status = 'ACTIVE'
        """)
    BigDecimal sumActiveBalancesByCustomer(@Param("customerId") UUID customerId);

    /** Branch-level total deposits (for branch reporting). */
    @Query("""
        SELECT COALESCE(SUM(a.balance), 0)
        FROM Account a
        WHERE a.branchId = :branchId
          AND a.accountType IN ('SAVINGS', 'CURRENT', 'OVERDRAFT')
          AND a.status = 'ACTIVE'
        """)
    BigDecimal sumDepositsByBranch(@Param("branchId") UUID branchId);

    // ── Bulk status update ────────────────────────────────────────

    /** Bulk freeze — called by the Fraud Detection Service. */
    @Modifying
    @Query("""
        UPDATE Account a
        SET a.isFrozen = true, a.updatedAt = CURRENT_TIMESTAMP
        WHERE a.customerId = :customerId
          AND a.status = 'ACTIVE'
        """)
    int bulkFreezeByCustomer(@Param("customerId") UUID customerId);

    /** Bulk unfreeze — called when KYC is verified. */
    @Modifying
    @Query("""
        UPDATE Account a
        SET a.isFrozen = false, a.updatedAt = CURRENT_TIMESTAMP
        WHERE a.customerId = :customerId
        """)
    int bulkUnfreezeByCustomer(@Param("customerId") UUID customerId);

    // ── Type-specific ─────────────────────────────────────────────
    List<Account> findByParentAccountId(UUID parentAccountId);

    Page<Account> findByAccountTypeAndStatus(AccountType type,
                                             AccountStatus status,
                                             Pageable pageable);
}