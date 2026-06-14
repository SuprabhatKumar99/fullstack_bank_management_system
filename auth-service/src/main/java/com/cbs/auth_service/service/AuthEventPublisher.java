package com.cbs.auth_service.service;


import java.util.concurrent.CompletableFuture;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import com.cbs.auth_service.dto.response.AuthEvent;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * Publishes {@link AuthEvent} messages to the {@code auth.events} Kafka topic.
 *
 * <p>Uses the {@code userId} as the partition key so all events for a given user
 * land on the same partition, preserving order for downstream consumers.
 *
 * <p>Failures are logged but never propagate to the caller — an auth event
 * publishing failure must never block or roll back the login/register transaction.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class AuthEventPublisher {

    private final KafkaTemplate<String, AuthEvent> kafkaTemplate;

    @Value("${kafka.topics.auth-events:auth.events}")
    private String topic;

    /**
     * Publishes an {@link AuthEvent} asynchronously.
     *
     * @param event the auth domain event to publish
     */
    public void publish(AuthEvent event) {
        String partitionKey = event.getUserId();   // same user → same partition → ordered events

        CompletableFuture<SendResult<String, AuthEvent>> future =
            kafkaTemplate.send(topic, partitionKey, event);

        future.whenComplete((result, ex) -> {
            if (ex != null) {
                log.error("Failed to publish AuthEvent [type={}, userId={}]: {}",
                    event.getEventType(), event.getUserId(), ex.getMessage());
            } else {
                log.debug("AuthEvent published [type={}, userId={}, partition={}, offset={}]",
                    event.getEventType(),
                    event.getUserId(),
                    result.getRecordMetadata().partition(),
                    result.getRecordMetadata().offset());
            }
        });
    }
}