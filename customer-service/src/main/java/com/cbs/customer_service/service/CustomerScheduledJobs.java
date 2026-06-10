package com.cbs.customer_service.service;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

/**
 * Nightly scheduled jobs for the Customer Service.
 *
 * Enable scheduling by adding @EnableScheduling to the main application class.
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerScheduledJobs {

    private final CustomerService customerService;

    /**
     * Runs every night at 00:05 IST.
     * Finds all VERIFIED customers whose kycExpiresAt < NOW()
     * and bulk-updates their status to EXPIRED.
     *
     * Customers with EXPIRED KYC cannot open new accounts or
     * initiate high-value transactions until they complete re-KYC.
     */
    @Scheduled(cron = "0 5 0 * * *", zone = "Asia/Kolkata")
    public void expireStaleKyc() {
        log.info("KYC expiry job started");
        try {
            int count = customerService.expireStaleKyc();
            log.info("KYC expiry job finished: {} customers expired", count);
        } catch (Exception ex) {
            log.error("KYC expiry job failed", ex);
        }
    }
}