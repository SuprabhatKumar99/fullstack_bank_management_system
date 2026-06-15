package com.cbs.customer_service.event;

import com.fasterxml.jackson.annotation.JsonFormat;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Kafka event published by Customer Service on significant lifecycle changes.
 * Consumed by Account Service, Notification Service, Audit Service, etc.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class CustomerEvent {

    public enum EventType {
        CUSTOMER_REGISTERED,
        KYC_UPDATED,
        PROFILE_UPDATED,
        CUSTOMER_DEACTIVATED
    }

    private String eventId;
    private EventType eventType;
    private String customerId;
    private String customerNumber;
    private String email;
    private String kycStatus;
    private boolean active;

    @Builder.Default
    @JsonFormat(pattern = "yyyy-MM-dd'T'HH:mm:ss")
    private LocalDateTime occurredAt = LocalDateTime.now();
}