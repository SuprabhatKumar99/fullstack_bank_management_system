package com.cbs.customer_service.service.impl;

import java.util.UUID;

import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.cbs.customer_service.dto.request.AddressRequest;
import com.cbs.customer_service.dto.request.CustomerProfileUpdateRequest;
import com.cbs.customer_service.dto.request.CustomerRegistrationRequest;
import com.cbs.customer_service.dto.request.KycUpdateRequest;
import com.cbs.customer_service.dto.response.AddressResponse;
import com.cbs.customer_service.dto.response.CustomerResponse;
import com.cbs.customer_service.dto.response.PagedResponse;
import com.cbs.customer_service.entity.Customer;
import com.cbs.customer_service.entity.CustomerAddress;
import com.cbs.customer_service.enums.KycStatus;
import com.cbs.customer_service.event.CustomerEvent;
import com.cbs.customer_service.exception.CustomerAlreadyExistsException;
import com.cbs.customer_service.exception.CustomerNotFoundException;
import com.cbs.customer_service.exception.InvalidKycTransitionException;
import com.cbs.customer_service.mapper.CustomerMapper;
import com.cbs.customer_service.repository.CustomerAddressRepository;
import com.cbs.customer_service.repository.CustomerRepository;
import com.cbs.customer_service.service.CustomerNumberGenerator;
import com.cbs.customer_service.service.CustomerService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class CustomerServiceImpl implements CustomerService {

    private final CustomerRepository           customerRepository;
    private final CustomerAddressRepository    addressRepository;
    private final CustomerMapper               customerMapper;
    private final CustomerNumberGenerator      customerNumberGenerator;
    private final KafkaTemplate<String, Object> kafkaTemplate;

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    @Override
    @Transactional
    public CustomerResponse registerCustomer(CustomerRegistrationRequest request) {
        log.info("Registering new customer with email: {}", request.getEmail());

        // Uniqueness guards
        if (customerRepository.existsByEmail(request.getEmail())) {
            throw new CustomerAlreadyExistsException("A customer with email [" + request.getEmail() + "] already exists.");
        }
        if (customerRepository.existsByPhoneNumber(request.getPhoneNumber())) {
            throw new CustomerAlreadyExistsException("A customer with phone number [" + request.getPhoneNumber() + "] already exists.");
        }
        if (request.getNationalId() != null && customerRepository.existsByNationalId(request.getNationalId())) {
            throw new CustomerAlreadyExistsException("A customer with national ID [" + request.getNationalId() + "] already exists.");
        }

        // Map & initialise
        Customer customer = customerMapper.toEntity(request);
        customer.setCustomerNumber(customerNumberGenerator.generate());
        customer.setKycStatus(KycStatus.PENDING);
        customer.setActive(true);
        customer.setRiskCategory("LOW");

        // Persist primary address if provided
        if (request.getPrimaryAddress() != null) {
            CustomerAddress address = customerMapper.toAddressEntity(request.getPrimaryAddress());
            address.setCustomer(customer);
            address.setPrimary(true);
            customer.getAddresses().add(address);
        }

        Customer saved = customerRepository.save(customer);
        log.info("Customer registered successfully: id={}, customerNumber={}", saved.getId(), saved.getCustomerNumber());

        // Publish event
        publishCustomerEvent("customer.registered", CustomerEvent.EventType.CUSTOMER_REGISTERED, saved);

        return customerMapper.toResponse(saved);
    }

    // -----------------------------------------------------------------------
    // Reads (Cacheable)
    // -----------------------------------------------------------------------

    @Override
    @Cacheable(value = "customers", key = "#id")
    public CustomerResponse getCustomerById(UUID id) {
        return customerMapper.toResponse(findCustomerOrThrow(id));
    }

    @Override
    @Cacheable(value = "customers", key = "#customerNumber")
    public CustomerResponse getCustomerByNumber(String customerNumber) {
        Customer customer = customerRepository.findByCustomerNumber(customerNumber)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with number: " + customerNumber));
        return customerMapper.toResponse(customer);
    }

    @Override
    @Cacheable(value = "customers", key = "#email")
    public CustomerResponse getCustomerByEmail(String email) {
        Customer customer = customerRepository.findByEmail(email)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with email: " + email));
        return customerMapper.toResponse(customer);
    }

    @Override
    public PagedResponse<CustomerResponse> listCustomers(Pageable pageable) {
        Page<CustomerResponse> page = customerRepository
                .findAllByActiveTrue(pageable)
                .map(customerMapper::toResponse);
        return PagedResponse.from(page);
    }

    @Override
    public PagedResponse<CustomerResponse> listCustomersByKycStatus(KycStatus status, Pageable pageable) {
        Page<CustomerResponse> page = customerRepository
                .findAllByKycStatusAndActiveTrue(status, pageable)
                .map(customerMapper::toResponse);
        return PagedResponse.from(page);
    }

    @Override
    public PagedResponse<CustomerResponse> searchCustomers(String query, Pageable pageable) {
        Page<CustomerResponse> page = customerRepository
                .searchCustomers(query, pageable)
                .map(customerMapper::toResponse);
        return PagedResponse.from(page);
    }

    // -----------------------------------------------------------------------
    // Updates
    // -----------------------------------------------------------------------

    @Override
    @Transactional
    @CachePut(value = "customers", key = "#id")
    public CustomerResponse updateProfile(UUID id, CustomerProfileUpdateRequest request) {
        log.info("Updating profile for customer id={}", id);
        Customer customer = findCustomerOrThrow(id);

        // Check uniqueness for changed fields
        if (request.getEmail() != null && !request.getEmail().equals(customer.getEmail())) {
            if (customerRepository.existsByEmail(request.getEmail())) {
                throw new CustomerAlreadyExistsException("Email [" + request.getEmail() + "] is already in use.");
            }
        }
        if (request.getPhoneNumber() != null && !request.getPhoneNumber().equals(customer.getPhoneNumber())) {
            if (customerRepository.existsByPhoneNumber(request.getPhoneNumber())) {
                throw new CustomerAlreadyExistsException("Phone number [" + request.getPhoneNumber() + "] is already in use.");
            }
        }

        customerMapper.updateCustomerFromRequest(request, customer);
        Customer saved = customerRepository.save(customer);
        log.info("Profile updated for customer id={}", id);

        publishCustomerEvent("customer.profile.updated", CustomerEvent.EventType.PROFILE_UPDATED, saved);

        return customerMapper.toResponse(saved);
    }

    @Override
    @Transactional
    @CachePut(value = "customers", key = "#id")
    public CustomerResponse updateKycStatus(UUID id, KycUpdateRequest request) {
        log.info("Updating KYC status for customer id={} to {}", id, request.getNewStatus());
        Customer customer = findCustomerOrThrow(id);

        try {
            customer.transitionKyc(request.getNewStatus(), request.getRejectionReason());
        } catch (IllegalStateException e) {
            throw new InvalidKycTransitionException(e.getMessage());
        }

        Customer saved = customerRepository.save(customer);
        log.info("KYC status updated for customer id={}: status={}", id, saved.getKycStatus());

        publishCustomerEvent("customer.kyc.updated", CustomerEvent.EventType.KYC_UPDATED, saved);

        return customerMapper.toResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = "customers", key = "#id")
    public void deactivateCustomer(UUID id) {
        log.info("Deactivating customer id={}", id);
        Customer customer = findCustomerOrThrow(id);
        customer.deactivate();
        customerRepository.save(customer);
        log.info("Customer id={} deactivated", id);
    }

    // -----------------------------------------------------------------------
    // Address Management
    // -----------------------------------------------------------------------

    @Override
    @Transactional
    @CacheEvict(value = "customers", key = "#customerId")
    public AddressResponse addAddress(UUID customerId, AddressRequest request) {
        log.info("Adding {} address for customer id={}", request.getAddressType(), customerId);
        Customer customer = findCustomerOrThrow(customerId);

        if (request.isPrimary()) {
            // Clear existing primary flag
            addressRepository.clearPrimaryForCustomer(customerId);
        }

        CustomerAddress address = customerMapper.toAddressEntity(request);
        address.setCustomer(customer);
        CustomerAddress saved = addressRepository.save(address);

        return customerMapper.toAddressResponse(saved);
    }

    @Override
    @Transactional
    @CacheEvict(value = "customers", key = "#customerId")
    public AddressResponse updateAddress(UUID customerId, UUID addressId, AddressRequest request) {
        CustomerAddress address = addressRepository.findById(addressId)
                .filter(a -> a.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Address not found: id=" + addressId + " for customer=" + customerId));

        if (request.isPrimary() && !address.isPrimary()) {
            addressRepository.clearPrimaryForCustomer(customerId);
        }

        address.setAddressType(request.getAddressType());
        address.setAddressLine1(request.getAddressLine1());
        address.setAddressLine2(request.getAddressLine2());
        address.setCity(request.getCity());
        address.setStateProvince(request.getStateProvince());
        address.setPostalCode(request.getPostalCode());
        address.setCountry(request.getCountry());
        address.setPrimary(request.isPrimary());

        return customerMapper.toAddressResponse(addressRepository.save(address));
    }

    @Override
    @Transactional
    @CacheEvict(value = "customers", key = "#customerId")
    public void removeAddress(UUID customerId, UUID addressId) {
        CustomerAddress address = addressRepository.findById(addressId)
                .filter(a -> a.getCustomer().getId().equals(customerId))
                .orElseThrow(() -> new CustomerNotFoundException(
                        "Address not found: id=" + addressId + " for customer=" + customerId));
        addressRepository.delete(address);
    }

    // -----------------------------------------------------------------------
    // Private helpers
    // -----------------------------------------------------------------------

    private Customer findCustomerOrThrow(UUID id) {
        return customerRepository.findById(id)
                .orElseThrow(() -> new CustomerNotFoundException("Customer not found with id: " + id));
    }

    private void publishCustomerEvent(String topic, CustomerEvent.EventType eventType, Customer customer) {
        try {
            CustomerEvent event = CustomerEvent.builder()
                    .eventId(UUID.randomUUID().toString())
                    .eventType(eventType)
                    .customerId(customer.getId().toString())
                    .customerNumber(customer.getCustomerNumber())
                    .email(customer.getEmail())
                    .kycStatus(customer.getKycStatus().name())
                    .active(customer.isActive())
                    .build();
            kafkaTemplate.send(topic, customer.getId().toString(), event);
            log.debug("Published event {} for customer {}", eventType, customer.getId());
        } catch (Exception e) {
            // Log and continue — Outbox pattern is the production-grade improvement here
            log.error("Failed to publish Kafka event {} for customer {}: {}", eventType, customer.getId(), e.getMessage());
        }
    }
}