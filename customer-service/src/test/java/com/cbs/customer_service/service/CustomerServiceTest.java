package com.cbs.customer_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;

import com.cbs.customer_service.dto.request.KycStatusUpdateRequest;
import com.cbs.customer_service.dto.request.RegisterCustomerRequest;
import com.cbs.customer_service.dto.request.UpdateProfileRequest;
import com.cbs.customer_service.dto.response.CustomerResponse;
import com.cbs.customer_service.entity.Customer;
import com.cbs.customer_service.enums.CustomerType;
import com.cbs.customer_service.enums.KycStatus;
import com.cbs.customer_service.exception.CustomerNotFoundException;
import com.cbs.customer_service.exception.DuplicateCustomerException;
import com.cbs.customer_service.exception.InvalidKycTransitionException;
import com.cbs.customer_service.mapper.CustomerMapper;
import com.cbs.customer_service.repository.CustomerRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerService")
class CustomerServiceTest {

    @Mock CustomerRepository   customerRepository;
    @Mock CustomerMapper       customerMapper;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks CustomerService customerService;

    // ── Fixtures ──────────────────────────────────────────────────

    private RegisterCustomerRequest validRequest;
    private Customer                savedCustomer;
    private CustomerResponse        customerResponse;
    private final UUID              customerId = UUID.randomUUID();
    private final UUID              staffId    = UUID.randomUUID();

    @BeforeEach
    void setUp() {
        validRequest = new RegisterCustomerRequest();
        validRequest.setCustomerType(CustomerType.INDIVIDUAL);
        validRequest.setFirstName("Ravi");
        validRequest.setLastName("Sharma");
        validRequest.setPhone("9876543210");
        validRequest.setEmail("ravi@example.com");
        validRequest.setPanNumber("ABCDE1234F");
        validRequest.setDateOfBirth(LocalDate.of(1990, 5, 15));

        savedCustomer = Customer.builder()
            .customerId(customerId)
            .customerType(CustomerType.INDIVIDUAL)
            .firstName("Ravi")
            .lastName("Sharma")
            .phone("9876543210")
            .email("ravi@example.com")
            .panNumber("ABCDE1234F")
            .kycStatus(KycStatus.PENDING)
            .build();

        customerResponse = CustomerResponse.builder()
            .customerId(customerId)
            .customerType(CustomerType.INDIVIDUAL)
            .firstName("Ravi")
            .lastName("Sharma")
            .kycStatus(KycStatus.PENDING)
            .build();
    }

    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("registerCustomer()")
    class RegisterCustomerTests {

        @Test
        @DisplayName("should register a valid INDIVIDUAL customer and publish event")
        void shouldRegisterValidCustomer() {
            given(customerRepository.existsByPhone(anyString())).willReturn(false);
            given(customerRepository.existsByEmail(anyString())).willReturn(false);
            given(customerRepository.existsByPanNumber(anyString())).willReturn(false);
            given(customerMapper.toEntity(any())).willReturn(savedCustomer);
            given(customerRepository.save(any())).willReturn(savedCustomer);
            given(customerMapper.toResponse(any())).willReturn(customerResponse);

            CustomerResponse result = customerService.registerCustomer(validRequest, staffId);

            assertThat(result).isNotNull();
            assertThat(result.getCustomerId()).isEqualTo(customerId);
            then(customerRepository).should().save(any(Customer.class));
            then(kafkaTemplate).should().send(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("should throw DuplicateCustomerException when phone already exists")
        void shouldThrowOnDuplicatePhone() {
            given(customerRepository.existsByPhone("9876543210")).willReturn(true);

            assertThatThrownBy(() ->
                customerService.registerCustomer(validRequest, staffId))
                .isInstanceOf(DuplicateCustomerException.class)
                .hasMessageContaining("9876543210");

            then(customerRepository).should(never()).save(any());
            then(kafkaTemplate).should(never()).send(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("should throw DuplicateCustomerException when PAN already exists")
        void shouldThrowOnDuplicatePan() {
            given(customerRepository.existsByPhone(anyString())).willReturn(false);
            given(customerRepository.existsByEmail(anyString())).willReturn(false);
            given(customerRepository.existsByPanNumber("ABCDE1234F")).willReturn(true);

            assertThatThrownBy(() ->
                customerService.registerCustomer(validRequest, staffId))
                .isInstanceOf(DuplicateCustomerException.class)
                .hasMessageContaining("ABCDE1234F");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when INDIVIDUAL has no first name")
        void shouldThrowWhenIndividualHasNoFirstName() {
            validRequest.setFirstName(null);

            assertThatThrownBy(() ->
                customerService.registerCustomer(validRequest, staffId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("First name is required");
        }

        @Test
        @DisplayName("should throw IllegalArgumentException when customer is under 18")
        void shouldThrowWhenUnder18() {
            validRequest.setDateOfBirth(LocalDate.now().minusYears(16));

            assertThatThrownBy(() ->
                customerService.registerCustomer(validRequest, staffId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("18 years");
        }

        @Test
        @DisplayName("should throw when BUSINESS customer has no business name")
        void shouldThrowWhenBusinessHasNoName() {
            validRequest.setCustomerType(CustomerType.BUSINESS);
            validRequest.setBusinessName(null);

            assertThatThrownBy(() ->
                customerService.registerCustomer(validRequest, staffId))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("Business name is required");
        }
    }

    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("updateKycStatus()")
    class KycStatusTests {

        @Test
        @DisplayName("PENDING → UNDER_REVIEW should succeed")
        void pendingToUnderReview() {
            savedCustomer.setKycStatus(KycStatus.PENDING);
            given(customerRepository.findById(customerId))
                .willReturn(Optional.of(savedCustomer));
            given(customerRepository.save(any())).willReturn(savedCustomer);
            given(customerMapper.toResponse(any())).willReturn(customerResponse);

            KycStatusUpdateRequest req = new KycStatusUpdateRequest();
            req.setStatus(KycStatus.UNDER_REVIEW);

            customerService.updateKycStatus(customerId, req, "officer-001");

            assertThat(savedCustomer.getKycStatus()).isEqualTo(KycStatus.UNDER_REVIEW);
            then(kafkaTemplate).should().send(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("UNDER_REVIEW → VERIFIED should set kycVerifiedAt and expiresAt")
        void underReviewToVerified() {
            savedCustomer.setKycStatus(KycStatus.UNDER_REVIEW);
            given(customerRepository.findById(customerId))
                .willReturn(Optional.of(savedCustomer));
            given(customerRepository.save(any())).willReturn(savedCustomer);
            given(customerMapper.toResponse(any())).willReturn(customerResponse);

            KycStatusUpdateRequest req = new KycStatusUpdateRequest();
            req.setStatus(KycStatus.VERIFIED);
            req.setExpiresAt(OffsetDateTime.now().plusYears(2));

            customerService.updateKycStatus(customerId, req, "officer-001");

            assertThat(savedCustomer.getKycStatus()).isEqualTo(KycStatus.VERIFIED);
            assertThat(savedCustomer.getKycVerifiedAt()).isNotNull();
            assertThat(savedCustomer.getKycExpiresAt()).isNotNull();
        }

        @Test
        @DisplayName("PENDING → VERIFIED should throw InvalidKycTransitionException")
        void pendingToVerifiedShouldFail() {
            savedCustomer.setKycStatus(KycStatus.PENDING);
            given(customerRepository.findById(customerId))
                .willReturn(Optional.of(savedCustomer));

            KycStatusUpdateRequest req = new KycStatusUpdateRequest();
            req.setStatus(KycStatus.VERIFIED);

            assertThatThrownBy(() ->
                customerService.updateKycStatus(customerId, req, "officer-001"))
                .isInstanceOf(InvalidKycTransitionException.class)
                .hasMessageContaining("PENDING")
                .hasMessageContaining("VERIFIED");
        }

        @Test
        @DisplayName("VERIFIED → REJECTED should throw InvalidKycTransitionException")
        void verifiedToRejectedShouldFail() {
            savedCustomer.setKycStatus(KycStatus.VERIFIED);
            given(customerRepository.findById(customerId))
                .willReturn(Optional.of(savedCustomer));

            KycStatusUpdateRequest req = new KycStatusUpdateRequest();
            req.setStatus(KycStatus.REJECTED);

            assertThatThrownBy(() ->
                customerService.updateKycStatus(customerId, req, "officer-001"))
                .isInstanceOf(InvalidKycTransitionException.class);
        }
    }

    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("getById()")
    class GetByIdTests {

        @Test
        @DisplayName("should return customer when found")
        void shouldReturnCustomer() {
            given(customerRepository.findById(customerId))
                .willReturn(Optional.of(savedCustomer));
            given(customerMapper.toResponse(savedCustomer))
                .willReturn(customerResponse);

            CustomerResponse result = customerService.getById(customerId);

            assertThat(result.getCustomerId()).isEqualTo(customerId);
        }

        @Test
        @DisplayName("should throw CustomerNotFoundException when not found")
        void shouldThrowWhenNotFound() {
            given(customerRepository.findById(customerId))
                .willReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.getById(customerId))
                .isInstanceOf(CustomerNotFoundException.class)
                .hasMessageContaining(customerId.toString());
        }
    }

    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("updateProfile()")
    class UpdateProfileTests {

        @Test
        @DisplayName("should update mutable fields and publish event")
        void shouldUpdateProfile() {
            given(customerRepository.findById(customerId))
                .willReturn(Optional.of(savedCustomer));
            given(customerRepository.existsByEmail(anyString())).willReturn(false);
            given(customerRepository.save(any())).willReturn(savedCustomer);
            given(customerMapper.toResponse(any())).willReturn(customerResponse);

            UpdateProfileRequest req = new UpdateProfileRequest();
            req.setEmail("new.email@example.com");
            req.setCity("Pune");

            customerService.updateProfile(customerId, req);

            then(customerMapper).should().updateEntityFromRequest(eq(req), eq(savedCustomer));
            then(customerRepository).should().save(savedCustomer);
            then(kafkaTemplate).should().send(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("should throw DuplicateCustomerException when new email already taken")
        void shouldThrowOnDuplicateEmail() {
            savedCustomer.setEmail("old@example.com");
            given(customerRepository.findById(customerId))
                .willReturn(Optional.of(savedCustomer));
            given(customerRepository.existsByEmail("taken@example.com")).willReturn(true);

            UpdateProfileRequest req = new UpdateProfileRequest();
            req.setEmail("taken@example.com");

            assertThatThrownBy(() -> customerService.updateProfile(customerId, req))
                .isInstanceOf(DuplicateCustomerException.class)
                .hasMessageContaining("taken@example.com");
        }
    }
}