package com.cbs.account_service.event;



import com.cbs.account_service.repository.AccountRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

/**
 * Listens for Customer Service domain events and reacts accordingly.
 *
 * KYC state → account action mapping:
 * ┌──────────────────┬────────────────────────────────────────────┐
 * │ KYC Status       │ Account Action                             │
 * ├──────────────────┼────────────────────────────────────────────┤
 * │ VERIFIED         │ Unfreeze all accounts (bulk update)        │
 * │ REJECTED         │ Freeze all accounts (quick-freeze flag)    │
 * │ EXPIRED          │ Freeze all accounts (quick-freeze flag)    │
 * │ UNDER_REVIEW     │ No action (already frozen from PENDING)    │
 * └──────────────────┴────────────────────────────────────────────┘
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class CustomerEventConsumer {

    private final AccountRepository accountRepository;

    @KafkaListener(
        topics   = "${kafka.topics.kyc-status-changed}",
        groupId  = "${spring.kafka.consumer.group-id}"
    )
    @Transactional
    public void onKycStatusChanged(KycStatusChangedEvent event) {
        log.info("KYC status changed for customer {}: {} → {}",
            event.getCustomerId(), event.getPreviousStatus(), event.getNewStatus());

        switch (event.getNewStatus()) {
            case "VERIFIED" -> {
                int count = accountRepository.bulkUnfreezeByCustomer(event.getCustomerId());
                log.info("Unfroze {} accounts for customer {} after KYC verification",
                    count, event.getCustomerId());
            }
            case "REJECTED", "EXPIRED" -> {
                int count = accountRepository.bulkFreezeByCustomer(event.getCustomerId());
                log.info("Froze {} accounts for customer {} due to KYC {}",
                    count, event.getCustomerId(), event.getNewStatus());
            }
            default -> log.debug("No account action needed for KYC status: {}",
                event.getNewStatus());
        }
    }
}