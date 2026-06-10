package com.cbs.customer_service.service;

import java.time.OffsetDateTime;
import java.util.Set;
import java.util.UUID;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cbs.customer_service.config.CustomerRegisteredEvent;
import com.cbs.customer_service.config.KycStatusChangedEvent;
import com.cbs.customer_service.dto.request.KycStatusUpdateRequest;
import com.cbs.customer_service.dto.request.RegisterCustomerRequest;
import com.cbs.customer_service.dto.request.UpdateProfileRequest;
import com.cbs.customer_service.dto.response.CustomerResponse;
import com.cbs.customer_service.dto.response.CustomerSummaryResponse;
import com.cbs.customer_service.entity.Customer;
import com.cbs.customer_service.enums.CustomerType;
import com.cbs.customer_service.enums.KycStatus;
import com.cbs.customer_service.exception.CustomerNotFoundException;
import com.cbs.customer_service.exception.DuplicateCustomerException;
import com.cbs.customer_service.exception.InvalidKycTransitionException;
import com.cbs.customer_service.mapper.CustomerMapper;
import com.cbs.customer_service.repository.CustomerRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerService {

    private final CustomerRepository customerRepository;
    private final CustomerMapper      customerMapper;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${kafka.topics.customer-registered}")
    private String customerRegisteredTopic;

    @Value("${kafka.topics.kyc-status-changed}")
    private String kycStatusChangedTopic;

    @Value("${kafka.topics.customer-updated}")
    private String customerUpdatedTopic;

    // ── KYC state machine ─────────────────────────────────────────
    // Defines which transitions are permitted.
    // Key = current status, Value = allowed next statuses.
    private static final java.util.Map<KycStatus, Set<KycStatus>> ALLOWED_KYC_TRANSITIONS =
        java.util.Map.of(
            KycStatus.PENDING,       Set.of(KycStatus.UNDER_REVIEW),
            KycStatus.UNDER_REVIEW,  Set.of(KycStatus.VERIFIED, KycStatus.REJECTED),
            KycStatus.REJECTED,      Set.of(KycStatus.UNDER_REVIEW),   // re-submission
            KycStatus.VERIFIED,      Set.of(KycStatus.EXPIRED),        // nightly job only
            KycStatus.EXPIRED,       Set.of(KycStatus.UNDER_REVIEW)    // re-KYC
        );

    // ─────────────────────────────────────────────────────────────
    // 1. REGISTRATION
    // ─────────────────────────────────────────────────────────────

    /**
     * Registers a new customer.
     *
     * Steps:
     *  1. Validate cross-field business rules (individual vs business).
     *  2. Check uniqueness (phone, email, PAN).
     *  3. Map request → entity and persist.
     *  4. Publish CustomerRegisteredEvent to Kafka.
     *
     * @Transactional ensures the DB write and the Kafka publish happen
     * atomically from the service's perspective. In production, use
     * the Outbox Pattern for true atomicity.
     */
    @Transactional
    public CustomerResponse registerCustomer(RegisterCustomerRequest request,
                                            UUID createdByStaffId) {
        // Step 1 — cross-field validation
        validateRegistrationRequest(request);

        // Step 2 — uniqueness checks
        if (customerRepository.existsByPhone(request.getPhone())) {
            throw new DuplicateCustomerException(
                "A customer with phone " + request.getPhone() + " already exists.");
        }
        if (request.getEmail() != null
                && customerRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateCustomerException(
                "A customer with email " + request.getEmail() + " already exists.");
        }
        if (request.getPanNumber() != null
                && customerRepository.existsByPanNumber(request.getPanNumber())) {
            throw new DuplicateCustomerException(
                "A customer with PAN " + request.getPanNumber() + " already exists.");
        }

        // Step 3 — persist
        Customer customer = customerMapper.toEntity(request);
        customer.setCreatedBy(createdByStaffId);
        Customer saved = customerRepository.save(customer);

        log.info("Customer registered: id={} type={} phone={}",
            saved.getCustomerId(), saved.getCustomerType(), saved.getPhone());

        // Step 4 — publish domain event
        CustomerRegisteredEvent event = CustomerRegisteredEvent.builder()
            .customerId(saved.getCustomerId())
            .customerType(saved.getCustomerType())
            .displayName(saved.getDisplayName())
            .phone(saved.getPhone())
            .email(saved.getEmail())
            .homeBranchId(saved.getHomeBranchId())
            .occurredAt(OffsetDateTime.now())
            .build();

        kafkaTemplate.send(customerRegisteredTopic,
            saved.getCustomerId().toString(), event);

        return customerMapper.toResponse(saved);
    }

    // ─────────────────────────────────────────────────────────────
    // 2. PROFILE READS
    // ─────────────────────────────────────────────────────────────

    @Transactional(readOnly = true)
    public CustomerResponse getById(UUID customerId) {
        return customerMapper.toResponse(findOrThrow(customerId));
    }

    @Transactional(readOnly = true)
    public CustomerResponse getByPhone(String phone) {
        Customer customer = customerRepository.findByPhone(phone)
            .orElseThrow(() -> new CustomerNotFoundException(
                "No customer found with phone: " + phone));
        return customerMapper.toResponse(customer);
    }

    @Transactional(readOnly = true)
    public CustomerResponse getByPan(String pan) {
        Customer customer = customerRepository.findByPanNumber(pan.toUpperCase())
            .orElseThrow(() -> new CustomerNotFoundException(
                "No customer found with PAN: " + pan));
        return customerMapper.toResponse(customer);
    }

    /**
     * Teller UI search — by first/last name, paginated.
     */
    @Transactional(readOnly = true)
    public Page<CustomerSummaryResponse> searchByName(String firstName,
                                                      String lastName,
                                                      Pageable pageable) {
        return customerRepository
            .searchByName(firstName, lastName, pageable)
            .map(customerMapper::toSummary);
    }

    /**
     * KYC pipeline — list customers by status (for the KYC officer dashboard).
     */
    @Transactional(readOnly = true)
    public Page<CustomerSummaryResponse> listByKycStatus(KycStatus status,
                                                         Pageable pageable) {
        return customerRepository
            .findByKycStatus(status, pageable)
            .map(customerMapper::toSummary);
    }

    // ─────────────────────────────────────────────────────────────
    // 3. PROFILE UPDATE
    // ─────────────────────────────────────────────────────────────

    /**
     * PATCH — only provided fields are updated (null fields ignored by MapStruct).
     * Immutable fields (phone, PAN, customerType) are excluded from the mapper.
     */
    @Transactional
    public CustomerResponse updateProfile(UUID customerId, UpdateProfileRequest request) {
        Customer customer = findOrThrow(customerId);

        // Email uniqueness check (if being changed)
        if (request.getEmail() != null
                && !request.getEmail().equalsIgnoreCase(customer.getEmail())
                && customerRepository.existsByEmail(request.getEmail())) {
            throw new DuplicateCustomerException(
                "Email " + request.getEmail() + " is already in use.");
        }

        customerMapper.updateEntityFromRequest(request, customer);
        Customer updated = customerRepository.save(customer);

        log.info("Customer profile updated: id={}", customerId);

        kafkaTemplate.send(customerUpdatedTopic,
            customerId.toString(),
            java.util.Map.of(
                "customerId", customerId,
                "occurredAt", OffsetDateTime.now()
            ));

        return customerMapper.toResponse(updated);
    }

    // ─────────────────────────────────────────────────────────────
    // 4. KYC STATUS UPDATE
    // ─────────────────────────────────────────────────────────────

    /**
     * Updates KYC status through the defined state machine.
     *
     * Only these transitions are allowed:
     *   PENDING       → UNDER_REVIEW
     *   UNDER_REVIEW  → VERIFIED | REJECTED
     *   REJECTED      → UNDER_REVIEW  (re-submission)
     *   VERIFIED      → EXPIRED       (nightly job)
     *   EXPIRED       → UNDER_REVIEW  (re-KYC)
     *
     * Any other transition throws InvalidKycTransitionException.
     */
    @Transactional
    public CustomerResponse updateKycStatus(UUID customerId,
                                            KycStatusUpdateRequest request,
                                            String changedByOfficerId) {
        Customer customer = findOrThrow(customerId);
        KycStatus current = customer.getKycStatus();
        KycStatus next    = request.getStatus();

        // Validate transition
        if (!ALLOWED_KYC_TRANSITIONS.getOrDefault(current, Set.of()).contains(next)) {
            throw new InvalidKycTransitionException(current, next);
        }

        // Apply transition using entity domain methods
        switch (next) {
            case UNDER_REVIEW -> customer.submitForKycReview();
            case VERIFIED -> {
                OffsetDateTime expiresAt = request.getExpiresAt() != null
                    ? request.getExpiresAt()
                    : OffsetDateTime.now().plusYears(2);   // RBI default: 2-year KYC validity
                customer.approveKyc(expiresAt);
            }
            case REJECTED -> customer.rejectKyc();
            case EXPIRED  -> customer.expireKyc();
            default -> throw new InvalidKycTransitionException(current, next);
        }

        Customer updated = customerRepository.save(customer);

        log.info("KYC status changed: customerId={} {} → {} by={}",
            customerId, current, next, changedByOfficerId);

        // Publish event — Account Service listens to unblock/block accounts
        KycStatusChangedEvent event = KycStatusChangedEvent.builder()
            .customerId(customerId)
            .previousStatus(current)
            .newStatus(next)
            .changedBy(changedByOfficerId)
            .rejectionReason(request.getRejectionReason())
            .expiresAt(updated.getKycExpiresAt())
            .occurredAt(OffsetDateTime.now())
            .build();

        kafkaTemplate.send(kycStatusChangedTopic, customerId.toString(), event);

        return customerMapper.toResponse(updated);
    }

    // ─────────────────────────────────────────────────────────────
    // 5. NIGHTLY BATCH — KYC EXPIRY
    // ─────────────────────────────────────────────────────────────

    /**
     * Called by the scheduled job every night at 00:05.
     * Bulk-expires all VERIFIED customers past their KYC expiry date.
     * Returns the count of expired customers.
     */
    @Transactional
    public int expireStaleKyc() {
        int count = customerRepository.bulkExpireKyc(OffsetDateTime.now());
        log.info("KYC expiry job completed: {} customers expired", count);
        return count;
    }

    // ─────────────────────────────────────────────────────────────
    // PRIVATE HELPERS
    // ─────────────────────────────────────────────────────────────

    private Customer findOrThrow(UUID customerId) {
        return customerRepository.findById(customerId)
            .orElseThrow(() -> new CustomerNotFoundException(
                "Customer not found: " + customerId));
    }

    /**
     * Cross-field validation that can't be expressed with JSR-380 annotations.
     * - INDIVIDUAL must have first + last name.
     * - BUSINESS must have businessName.
     * - INDIVIDUAL must be 18+ years old (also enforced at DB level).
     */
    private void validateRegistrationRequest(RegisterCustomerRequest req) {
        if (req.getCustomerType() == CustomerType.INDIVIDUAL) {
            if (req.getFirstName() == null || req.getFirstName().isBlank()) {
                throw new IllegalArgumentException("First name is required for INDIVIDUAL customers.");
            }
            if (req.getLastName() == null || req.getLastName().isBlank()) {
                throw new IllegalArgumentException("Last name is required for INDIVIDUAL customers.");
            }
            if (req.getDateOfBirth() != null
                    && req.getDateOfBirth().isAfter(
                        java.time.LocalDate.now().minusYears(18))) {
                throw new IllegalArgumentException(
                    "Customer must be at least 18 years old.");
            }
        }
        if (req.getCustomerType() == CustomerType.BUSINESS) {
            if (req.getBusinessName() == null || req.getBusinessName().isBlank()) {
                throw new IllegalArgumentException("Business name is required for BUSINESS customers.");
            }
        }
    }
}