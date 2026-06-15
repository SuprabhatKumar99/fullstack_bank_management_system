package com.cbs.customer_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.cache.annotation.EnableCaching;
import org.springframework.kafka.annotation.EnableKafka;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * CBS Customer Service
 *
 * Responsibilities:
 *  - Customer registration (INDIVIDUAL + BUSINESS)
 *  - KYC lifecycle management (PENDING → VERIFIED → EXPIRED)
 *  - Profile reads and updates
 *  - Publishes domain events to Kafka (CustomerRegistered, KycStatusChanged)
 *
 * Port: 8081
 */

@SpringBootApplication
@EnableCaching
@EnableKafka
@EnableScheduling
public class CustomerServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(CustomerServiceApplication.class, args);
	}

}
