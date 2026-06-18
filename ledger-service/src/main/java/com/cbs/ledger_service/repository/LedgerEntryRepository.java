package com.cbs.ledger_service.repository;


import java.time.LocalDate;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cbs.ledger_service.entity.LedgerEntry;

@Repository
public interface LedgerEntryRepository extends JpaRepository<LedgerEntry, UUID> {

    // All entries for a transaction (typically 2 rows)
    List<LedgerEntry> findByTransactionId(UUID transactionId);

    // Account statement with date range (used by Ledger Service)
    Page<LedgerEntry> findByAccountIdAndValueDateBetweenOrderByCreatedAtDesc(
        UUID accountId, LocalDate from, LocalDate to, Pageable pageable);

    // Balance check: recompute from ledger (integrity audit)
    @Query("""
        SELECT
            COALESCE(SUM(e.amount) FILTER (WHERE e.entryType = 'CREDIT'), 0)
          - COALESCE(SUM(e.amount) FILTER (WHERE e.entryType = 'DEBIT'),  0)
        FROM LedgerEntry e
        WHERE e.accountId = :accountId
        """)
    java.math.BigDecimal computeLedgerBalance(@Param("accountId") UUID accountId);

    // GL report entries by code and date
    Page<LedgerEntry> findByGlAccountCodeAndValueDateBetweenOrderByValueDateDesc(
        String glAccountCode, LocalDate from, LocalDate to, Pageable pageable);

    // Latest entry for an account (for balance_after snapshot)
    @Query("""
        SELECT e FROM LedgerEntry e
        WHERE e.accountId = :accountId
        ORDER BY e.createdAt DESC
        LIMIT 1
        """)
    java.util.Optional<LedgerEntry> findLatestByAccountId(@Param("accountId") UUID accountId);
}