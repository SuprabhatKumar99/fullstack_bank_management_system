package com.cbs.customer_service.enums;

public enum KycStatus {
    PENDING,
    UNDER_REVIEW,
    VERIFIED,
    REJECTED,
    EXPIRED;
 
    /** Returns true if this customer may open accounts and transact freely. */
    public boolean allowsTransactions() {
        return this == VERIFIED;
    }
}
 