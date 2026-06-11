package com.cbs.account_service.enums;

public enum AccountStatus {
    PENDING_ACTIVATION,   // created but not yet operational
    ACTIVE,               // fully operational
    DORMANT,              // no tx for > 2 years (RBI); restricted operations
    FROZEN,               // bank/court order; no debits or credits allowed
    CLOSED;               // permanently closed

    public boolean isOperational() {
        return this == ACTIVE;
    }

    public boolean allowsRead() {
        return this != CLOSED;
    }
}