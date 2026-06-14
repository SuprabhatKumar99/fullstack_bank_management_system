package com.cbs.auth_service.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

/**
 * Typed binding for the {@code jwt.*} block in application.yml.
 */
@Component
@ConfigurationProperties(prefix = "jwt")
@Getter
@Setter
public class JwtProperties {

    /** HMAC-SHA256 signing secret (min 32 chars in prod; use a vault-injected env var). */
    private String secret;

    /** Access token lifetime in milliseconds. Default: 15 minutes. */
    private long accessTokenExpiryMs = 900_000L;

    /** Refresh token lifetime in milliseconds. Default: 7 days. */
    private long refreshTokenExpiryMs = 604_800_000L;

    /** JWT {@code iss} claim value. */
    private String issuer = "cbs-auth-service";
}