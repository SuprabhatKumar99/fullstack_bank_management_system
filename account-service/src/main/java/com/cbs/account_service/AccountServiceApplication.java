package com.cbs.account_service;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;



/**
 * CBS Account Service
 *
 * Responsibilities:
 *  - Account CRUD for all types: SAVINGS, CURRENT, FD, RD, LOAN, OVERDRAFT
 *  - Balance enquiry with Redis caching (5-min TTL)
 *  - Account lifecycle: open → activate → freeze/unfreeze → close
 *  - Hold management: place/release funds holds
 *  - Validates debit eligibility (called by Transaction Service)
 *  - Consumes KYC events to freeze/unfreeze accounts automatically
 *  - Publishes account lifecycle events to Kafka
 *  - Nightly jobs: dormancy detection, FD/RD maturity processing
 *
 * Port: 8082
 */
@SpringBootApplication
@EnableScheduling
public class AccountServiceApplication {

	public static void main(String[] args) {
		SpringApplication.run(AccountServiceApplication.class, args);
	}

}
