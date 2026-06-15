package com.cbs.customer_service.enums;

/**
 * Represents the KYC (Know Your Customer) verification lifecycle status of a customer.
 *
 * State machine transitions are enforced in {@link com.cbs.customerservice.domain.entity.Customer#transitionKyc}.
 *
 * <pre>
 *   PENDING → UNDER_REVIEW → APPROVED
 *                          → REJECTED
 *                          → ADDITIONAL_INFO_REQUIRED → UNDER_REVIEW
 *   APPROVED → SUSPENDED → APPROVED
 *                        → REJECTED
 * </pre>
 */
public enum KycStatus {
    PENDING,
    UNDER_REVIEW,
    ADDITIONAL_INFO_REQUIRED,
    APPROVED,
    SUSPENDED,
    REJECTED
}
 