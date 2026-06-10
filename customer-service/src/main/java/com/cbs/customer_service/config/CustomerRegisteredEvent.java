package com.cbs.customer_service.config;

import java.time.OffsetDateTime;
import java.util.UUID;

import com.cbs.customer_service.enums.CustomerType;

import lombok.Builder;
import lombok.Data;

/**
 * Kafka event published when a new customer is registered.
 * Topic: customer.registered
 *
 * Consumed by: Account Service (to allow account creation),
 *              Notification Service (welcome SMS/email).
 */
@Data
@Builder
public class CustomerRegisteredEvent {
    private UUID         customerId;
    private CustomerType customerType;
    private String       displayName;
    private String       phone;
    private String       email;
    private UUID         homeBranchId;
    private OffsetDateTime occurredAt;
}