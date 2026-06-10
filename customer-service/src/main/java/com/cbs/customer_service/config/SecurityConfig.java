package com.cbs.customer_service.config;


import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.oauth2.server.resource.authentication.JwtAuthenticationConverter;
import org.springframework.security.oauth2.server.resource.authentication.JwtGrantedAuthoritiesConverter;
import org.springframework.security.web.SecurityFilterChain;

/**
 * Security configuration for the Customer Service.
 *
 * Architecture: The API Gateway validates the JWT and forwards it as a
 * Bearer token. This service trusts the gateway and only re-validates the
 * token signature against the issuer's public key (configured via
 * spring.security.oauth2.resourceserver.jwt.issuer-uri).
 *
 * Role extraction: JWT claims contain a "roles" array.
 * JwtGrantedAuthoritiesConverter maps them to Spring Security ROLE_ prefixed
 * GrantedAuthority objects, enabling @PreAuthorize("hasRole('TELLER')") etc.
 */
@Configuration
@EnableWebSecurity
@EnableMethodSecurity(prePostEnabled = true)
public class SecurityConfig {

    @Bean
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
            .csrf(csrf -> csrf.disable())          // REST API — no CSRF needed
            .sessionManagement(sm ->
                sm.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
            .authorizeHttpRequests(auth -> auth
                // Actuator health check — open (for K8s liveness/readiness probes)
                .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                // All other endpoints require authentication
                .anyRequest().authenticated()
            )
            .oauth2ResourceServer(oauth2 -> oauth2
                .jwt(jwt -> jwt
                    .jwtAuthenticationConverter(jwtAuthenticationConverter())
                )
            );

        return http.build();
    }

    /**
     * Extracts roles from the "roles" claim in the JWT and maps them to
     * Spring Security GrantedAuthority with the ROLE_ prefix.
     *
     * JWT claim:  { "roles": ["TELLER", "ADMIN"] }
     * Mapped to:  [ROLE_TELLER, ROLE_ADMIN]
     */
    @Bean
    public JwtAuthenticationConverter jwtAuthenticationConverter() {
        JwtGrantedAuthoritiesConverter authoritiesConverter =
            new JwtGrantedAuthoritiesConverter();
        authoritiesConverter.setAuthoritiesClaimName("roles");
        authoritiesConverter.setAuthorityPrefix("ROLE_");

        JwtAuthenticationConverter converter = new JwtAuthenticationConverter();
        converter.setJwtGrantedAuthoritiesConverter(authoritiesConverter);
        return converter;
    }
}