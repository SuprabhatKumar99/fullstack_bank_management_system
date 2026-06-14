package com.cbs.auth_service.config;


import org.apache.kafka.clients.admin.NewTopic;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.config.TopicBuilder;

/**
 * Kafka topic declarations for the Auth Service.
 *
 * <p>Topic {@code auth.events} carries login, logout, registration,
 * lockout, and token-reuse events consumed by the audit/notification services.
 */
@Configuration
public class KafkaConfig {

    @Bean
    public NewTopic authEventsTopic() {
        return TopicBuilder.name("auth.events")
            .partitions(3)
            .replicas(1)          // increase to 3 in production
            .build();
    }
}