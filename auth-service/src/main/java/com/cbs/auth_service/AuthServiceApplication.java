package com.cbs.auth_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Authentication Service – the identity and token issuer for the entire CBS platform.
 *
 * <p>This service is the ONLY JWT issuer. All other microservices are JWT resource servers
 * that validate tokens signed here. Responsibilities:
 * <ul>
 *   <li>User registration and credential management</li>
 *   <li>Login → access token + refresh token issuance</li>
 *   <li>Token refresh (rotation strategy)</li>
 *   <li>Logout / token revocation</li>
 *   <li>Account lockout after failed attempts</li>
 *   <li>Publishing AuthEvents to Kafka for audit trail</li>
 * </ul>
 *
 * <p>Port: 8080 (all other services 808x, acting as downstream resource servers)
 */
@SpringBootApplication
@EnableScheduling
public class AuthServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(AuthServiceApplication.class, args);
    }
}
