package com.cbs.auth_service.dto.response;


import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Kafka message published to {@code auth.events} topic.
 *
 * <p>Consumed by:
 * <ul>
 *   <li>Audit Service – stores full event log</li>
 *   <li>Notification Service – sends login alerts if new device/IP detected</li>
 * </ul>
 */
@Data
@Builder
public class AuthEvent {

    public enum EventType {
        USER_REGISTERED,
        LOGIN_SUCCESS,
        LOGIN_FAILED,
        LOGOUT,
        TOKEN_REFRESHED,
        ACCOUNT_LOCKED,
        TOKEN_REUSE_DETECTED,
        PASSWORD_CHANGED
    }

    private EventType eventType;
    private String    userId;
    private String    username;
    private String    ipAddress;
    private String    userAgent;
    private Instant   occurredAt;
    private String    detail;           // optional context e.g. "Failed attempt 3/5"
}