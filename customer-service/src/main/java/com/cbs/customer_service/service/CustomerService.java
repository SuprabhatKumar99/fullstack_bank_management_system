package com.cbs.customer_service.service;

import java.util.UUID;

import org.springframework.data.domain.Pageable;

import com.cbs.customer_service.dto.request.AddressRequest;
import com.cbs.customer_service.dto.request.CustomerProfileUpdateRequest;
import com.cbs.customer_service.dto.request.CustomerRegistrationRequest;
import com.cbs.customer_service.dto.request.KycUpdateRequest;
import com.cbs.customer_service.dto.response.AddressResponse;
import com.cbs.customer_service.dto.response.CustomerResponse;
import com.cbs.customer_service.dto.response.PagedResponse;
import com.cbs.customer_service.enums.KycStatus;

public interface CustomerService {

    /**
     * Register a new customer. Assigns a unique customer number and sets KYC to PENDING.
     */
    CustomerResponse registerCustomer(CustomerRegistrationRequest request);

    /**
     * Retrieve a customer by their internal UUID.
     */
    CustomerResponse getCustomerById(UUID id);

    /**
     * Retrieve a customer by their business-facing customer number.
     */
    CustomerResponse getCustomerByNumber(String customerNumber);

    /**
     * Retrieve a customer by email address.
     */
    CustomerResponse getCustomerByEmail(String email);

    /**
     * List all active customers with pagination.
     */
    PagedResponse<CustomerResponse> listCustomers(Pageable pageable);

    /**
     * List customers filtered by KYC status.
     */
    PagedResponse<CustomerResponse> listCustomersByKycStatus(KycStatus status, Pageable pageable);

    /**
     * Search customers by name, email, or customer number.
     */
    PagedResponse<CustomerResponse> searchCustomers(String query, Pageable pageable);

    /**
     * Update mutable profile fields (name, email, phone, nationality).
     */
    CustomerResponse updateProfile(UUID id, CustomerProfileUpdateRequest request);

    /**
     * Transition KYC status following the state machine rules.
     */
    CustomerResponse updateKycStatus(UUID id, KycUpdateRequest request);

    /**
     * Soft-delete a customer (sets active = false).
     */
    void deactivateCustomer(UUID id);

    /**
     * Add an address to a customer's profile.
     */
    AddressResponse addAddress(UUID customerId, AddressRequest request);

    /**
     * Update an existing address.
     */
    AddressResponse updateAddress(UUID customerId, UUID addressId, AddressRequest request);

    /**
     * Remove an address from a customer's profile.
     */
    void removeAddress(UUID customerId, UUID addressId);
}