package com.cbs.ledger_service.entity;


import jakarta.persistence.*;
import lombok.*;

import java.math.BigDecimal;
import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

/**
 * Ledger entry entity — one row per debit/credit leg of a transaction.
 *
 * Owned by the Transaction Service because both Transaction and LedgerEntry
 * rows must be written in the SAME database transaction (atomicity).
 * The Ledger Service is a read-only query layer over this table.
 *
 * Double-entry rule: for every transaction_id,
 *   SUM(amount WHERE entry_type='DEBIT') == SUM(amount WHERE entry_type='CREDIT')
 * Enforced by the DB trigger fn_check_ledger_balance (deferred).
 */
@Entity
@Table(
    name = "ledger_entries",
    indexes = {
        @Index(name = "idx_le_account_date",  columnList = "account_id, value_date DESC"),
        @Index(name = "idx_le_transaction",   columnList = "transaction_id"),
        @Index(name = "idx_le_gl_code",       columnList = "gl_account_code, value_date DESC"),
        @Index(name = "idx_le_created_at",    columnList = "created_at DESC")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "entry_id", updatable = false, nullable = false)
    private UUID entryId;

    @Column(name = "transaction_id", nullable = false, updatable = false)
    private UUID transactionId;

    @Column(name = "account_id", nullable = false, updatable = false)
    private UUID accountId;

    /**
     * DEBIT  = money leaves the account (balance decreases).
     * CREDIT = money enters the account (balance increases).
     * amount is ALWAYS positive — direction is in entry_type.
     */
    @Column(name = "entry_type", nullable = false, length = 6)
    private String entryType;   // "DEBIT" | "CREDIT"

    @Column(name = "amount", nullable = false, precision = 18, scale = 2)
    private BigDecimal amount;

    @Column(name = "currency", nullable = false, length = 3)
    @Builder.Default
    private String currency = "INR";

    /**
     * Snapshot of account balance immediately AFTER this entry was applied.
     * Stored so statement generation is O(1) per entry rather than O(n)
     * cumulative sum over all historical entries.
     */
    @Column(name = "balance_after", nullable = false, precision = 18, scale = 2)
    private BigDecimal balanceAfter;

    @Column(name = "account_type", nullable = false, length = 15)
    @Builder.Default
    private String accountType = "LIABILITY";  // ASSET | LIABILITY | REVENUE | EXPENSE

    @Column(name = "gl_account_code", length = 20)
    private String glAccountCode;

    @Column(name = "narration", length = 200)
    private String narration;

    @Column(name = "value_date", nullable = false)
    @Builder.Default
    private LocalDate valueDate = LocalDate.now();

    @Column(name = "created_by", nullable = false, length = 60)
    @Builder.Default
    private String createdBy = "SYSTEM";

    @Column(name = "created_at", nullable = false, updatable = false)
    @Builder.Default
    private OffsetDateTime createdAt = OffsetDateTime.now();
}