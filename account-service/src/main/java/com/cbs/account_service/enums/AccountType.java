package com.cbs.account_service.enums;


/**
 * All account types supported by CBS.
 *
 * SAVINGS          - Individual retail savings (MAB required)
 * CURRENT          - Business / high-volume transacting (OD allowed)
 * FIXED_DEPOSIT    - Lump-sum deposit, locked for a tenure
 * RECURRING_DEPOSIT- Fixed monthly instalment, locked for a tenure
 * LOAN             - Disbursement account for sanctioned loans
 * OVERDRAFT        - Current account with pre-approved overdraft facility
 */
public enum AccountType {
    SAVINGS,
    CURRENT,
    FIXED_DEPOSIT,
    RECURRING_DEPOSIT,
    LOAN,
    OVERDRAFT;

    public boolean isDepositAccount() {
        return this == SAVINGS || this == CURRENT || this == OVERDRAFT;
    }

    public boolean isTermDeposit() {
        return this == FIXED_DEPOSIT || this == RECURRING_DEPOSIT;
    }

    public boolean allowsOverdraft() {
        return this == CURRENT || this == OVERDRAFT;
    }
}