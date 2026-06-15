package com.cbs.customer_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;
import static org.mockito.Mockito.never;

import java.time.LocalDate;
import java.util.HashSet;
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

import com.cbs.customer_service.dto.request.CustomerRegistrationRequest;
import com.cbs.customer_service.dto.request.KycUpdateRequest;
import com.cbs.customer_service.dto.response.CustomerResponse;
import com.cbs.customer_service.entity.Customer;
import com.cbs.customer_service.enums.KycStatus;
import com.cbs.customer_service.exception.CustomerAlreadyExistsException;
import com.cbs.customer_service.exception.CustomerNotFoundException;
import com.cbs.customer_service.exception.InvalidKycTransitionException;
import com.cbs.customer_service.mapper.CustomerMapper;
import com.cbs.customer_service.repository.CustomerAddressRepository;
import com.cbs.customer_service.repository.CustomerRepository;
import com.cbs.customer_service.service.impl.CustomerServiceImpl;

@ExtendWith(MockitoExtension.class)
@DisplayName("CustomerServiceImpl Unit Tests")
class CustomerServiceTest {

    @Mock private CustomerRepository        customerRepository;
    @Mock private CustomerAddressRepository addressRepository;
    @Mock private CustomerMapper            customerMapper;
    @Mock private CustomerNumberGenerator   customerNumberGenerator;
    @Mock private KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks
    private CustomerServiceImpl customerService;

    private CustomerRegistrationRequest registrationRequest;
    private Customer                    sampleCustomer;
    private CustomerResponse            sampleResponse;

    @BeforeEach
    void setUp() {
        registrationRequest = CustomerRegistrationRequest.builder()
                .firstName("Supra")
                .lastName("B")
                .email("supra@example.com")
                .phoneNumber("+919876543210")
                .dateOfBirth(LocalDate.of(1995, 6, 14))
                .build();

        sampleCustomer = Customer.builder()
                .id(UUID.randomUUID())
                .customerNumber("CBS-202406-00000001")
                .firstName("Supra")
                .lastName("B")
                .email("supra@example.com")
                .phoneNumber("+919876543210")
                .dateOfBirth(LocalDate.of(1995, 6, 14))
                .kycStatus(KycStatus.PENDING)
                .active(true)
                .addresses(new HashSet<>())
                .build();

        sampleResponse = CustomerResponse.builder()
                .id(sampleCustomer.getId())
                .customerNumber("CBS-202406-00000001")
                .firstName("Supra")
                .lastName("B")
                .email("supra@example.com")
                .kycStatus(KycStatus.PENDING)
                .active(true)
                .build();
    }

    // -----------------------------------------------------------------------
    // Registration Tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("registerCustomer()")
    class RegisterCustomerTests {

        @Test
        @DisplayName("should register customer successfully when all fields are unique")
        void shouldRegisterCustomerSuccessfully() {
            given(customerRepository.existsByEmail(anyString())).willReturn(false);
            given(customerRepository.existsByPhoneNumber(anyString())).willReturn(false);
            given(customerMapper.toEntity(any())).willReturn(sampleCustomer);
            given(customerNumberGenerator.generate()).willReturn("CBS-202406-00000001");
            given(customerRepository.save(any(Customer.class))).willReturn(sampleCustomer);
            given(customerMapper.toResponse(any(Customer.class))).willReturn(sampleResponse);

            CustomerResponse result = customerService.registerCustomer(registrationRequest);

            assertThat(result).isNotNull();
            assertThat(result.getEmail()).isEqualTo("supra@example.com");
            assertThat(result.getKycStatus()).isEqualTo(KycStatus.PENDING);

            then(customerRepository).should().save(any(Customer.class));
        }

        @Test
        @DisplayName("should throw CustomerAlreadyExistsException when email is taken")
        void shouldThrowWhenEmailAlreadyExists() {
            given(customerRepository.existsByEmail("supra@example.com")).willReturn(true);

            assertThatThrownBy(() -> customerService.registerCustomer(registrationRequest))
                    .isInstanceOf(CustomerAlreadyExistsException.class)
                    .hasMessageContaining("supra@example.com");

            then(customerRepository).should(never()).save(any());
        }

        @Test
        @DisplayName("should throw CustomerAlreadyExistsException when phone number is taken")
        void shouldThrowWhenPhoneAlreadyExists() {
            given(customerRepository.existsByEmail(anyString())).willReturn(false);
            given(customerRepository.existsByPhoneNumber(anyString())).willReturn(true);

            assertThatThrownBy(() -> customerService.registerCustomer(registrationRequest))
                    .isInstanceOf(CustomerAlreadyExistsException.class)
                    .hasMessageContaining("+919876543210");
        }
    }

    // -----------------------------------------------------------------------
    // Read Tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("getCustomerById()")
    class GetCustomerByIdTests {

        @Test
        @DisplayName("should return CustomerResponse for a known id")
        void shouldReturnCustomerWhenFound() {
            UUID id = sampleCustomer.getId();
            given(customerRepository.findById(id)).willReturn(Optional.of(sampleCustomer));
            given(customerMapper.toResponse(sampleCustomer)).willReturn(sampleResponse);

            CustomerResponse result = customerService.getCustomerById(id);

            assertThat(result).isNotNull();
            assertThat(result.getId()).isEqualTo(id);
        }

        @Test
        @DisplayName("should throw CustomerNotFoundException for an unknown id")
        void shouldThrowWhenNotFound() {
            UUID unknownId = UUID.randomUUID();
            given(customerRepository.findById(unknownId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.getCustomerById(unknownId))
                    .isInstanceOf(CustomerNotFoundException.class)
                    .hasMessageContaining(unknownId.toString());
        }
    }

    // -----------------------------------------------------------------------
    // KYC State Machine Tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("updateKycStatus()")
    class KycUpdateTests {

        @Test
        @DisplayName("should allow valid KYC transition PENDING → UNDER_REVIEW")
        void shouldAllowValidKycTransition() {
            UUID id = sampleCustomer.getId();
            KycUpdateRequest request = KycUpdateRequest.builder()
                    .newStatus(KycStatus.UNDER_REVIEW)
                    .build();

            given(customerRepository.findById(id)).willReturn(Optional.of(sampleCustomer));
            given(customerRepository.save(any(Customer.class))).willReturn(sampleCustomer);
            given(customerMapper.toResponse(any())).willReturn(sampleResponse);

            CustomerResponse result = customerService.updateKycStatus(id, request);

            assertThat(result).isNotNull();
            then(customerRepository).should().save(sampleCustomer);
        }

        @Test
        @DisplayName("should throw InvalidKycTransitionException for illegal transition PENDING → APPROVED")
        void shouldThrowForIllegalKycTransition() {
            UUID id = sampleCustomer.getId();
            KycUpdateRequest request = KycUpdateRequest.builder()
                    .newStatus(KycStatus.APPROVED)
                    .build();

            // PENDING → APPROVED is not a valid transition
            given(customerRepository.findById(id)).willReturn(Optional.of(sampleCustomer));

            assertThatThrownBy(() -> customerService.updateKycStatus(id, request))
                    .isInstanceOf(InvalidKycTransitionException.class);
        }

        @Test
        @DisplayName("should set kycVerifiedAt when transitioning to APPROVED")
        void shouldSetVerifiedAtOnApproval() {
            // Set up customer in UNDER_REVIEW state
            sampleCustomer.setKycStatus(KycStatus.UNDER_REVIEW);
            UUID id = sampleCustomer.getId();

            KycUpdateRequest request = KycUpdateRequest.builder()
                    .newStatus(KycStatus.APPROVED)
                    .build();

            given(customerRepository.findById(id)).willReturn(Optional.of(sampleCustomer));
            given(customerRepository.save(any(Customer.class))).willReturn(sampleCustomer);
            given(customerMapper.toResponse(any())).willReturn(sampleResponse);

            customerService.updateKycStatus(id, request);

            assertThat(sampleCustomer.getKycVerifiedAt()).isNotNull();
        }
    }

    // -----------------------------------------------------------------------
    // Deactivation Tests
    // -----------------------------------------------------------------------

    @Nested
    @DisplayName("deactivateCustomer()")
    class DeactivationTests {

        @Test
        @DisplayName("should deactivate an active customer")
        void shouldDeactivateCustomer() {
            UUID id = sampleCustomer.getId();
            given(customerRepository.findById(id)).willReturn(Optional.of(sampleCustomer));
            given(customerRepository.save(any(Customer.class))).willReturn(sampleCustomer);

            customerService.deactivateCustomer(id);

            assertThat(sampleCustomer.isActive()).isFalse();
            then(customerRepository).should().save(sampleCustomer);
        }

        @Test
        @DisplayName("should throw CustomerNotFoundException when deactivating unknown customer")
        void shouldThrowWhenDeactivatingUnknownCustomer() {
            UUID unknownId = UUID.randomUUID();
            given(customerRepository.findById(unknownId)).willReturn(Optional.empty());

            assertThatThrownBy(() -> customerService.deactivateCustomer(unknownId))
                    .isInstanceOf(CustomerNotFoundException.class);
        }
    }
}