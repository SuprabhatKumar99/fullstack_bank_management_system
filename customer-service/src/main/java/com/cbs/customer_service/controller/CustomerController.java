package com.cbs.customer_service.controller;

import java.util.UUID;

import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import com.cbs.customer_service.dto.request.AddressRequest;
import com.cbs.customer_service.dto.request.CustomerProfileUpdateRequest;
import com.cbs.customer_service.dto.request.CustomerRegistrationRequest;
import com.cbs.customer_service.dto.request.KycUpdateRequest;
import com.cbs.customer_service.dto.response.AddressResponse;
import com.cbs.customer_service.dto.response.CustomerResponse;
import com.cbs.customer_service.dto.response.PagedResponse;
import com.cbs.customer_service.enums.KycStatus;
import com.cbs.customer_service.service.CustomerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

@RestController
@RequestMapping("/v1/customers")
@RequiredArgsConstructor
@Slf4j
public class CustomerController {

    private final CustomerService customerService;

    // -----------------------------------------------------------------------
    // Registration
    // -----------------------------------------------------------------------

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public CustomerResponse registerCustomer(@Valid @RequestBody CustomerRegistrationRequest request) {
        log.info("POST /v1/customers - registering customer: {}", request.getEmail());
        return customerService.registerCustomer(request);
    }

    // -----------------------------------------------------------------------
    // Reads
    // -----------------------------------------------------------------------

    @GetMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CUSTOMER') or hasRole('ROLE_BANK_STAFF') or hasRole('ROLE_ADMIN')")
    public CustomerResponse getCustomerById(@PathVariable UUID id) {
        return customerService.getCustomerById(id);
    }

    @GetMapping("/number/{customerNumber}")
    @PreAuthorize("hasRole('ROLE_BANK_STAFF') or hasRole('ROLE_ADMIN')")
    public CustomerResponse getCustomerByNumber(@PathVariable String customerNumber) {
        return customerService.getCustomerByNumber(customerNumber);
    }

    @GetMapping("/email/{email}")
    @PreAuthorize("hasRole('ROLE_BANK_STAFF') or hasRole('ROLE_ADMIN')")
    public CustomerResponse getCustomerByEmail(@PathVariable String email) {
        return customerService.getCustomerByEmail(email);
    }

    @GetMapping
    @PreAuthorize("hasRole('ROLE_BANK_STAFF') or hasRole('ROLE_ADMIN')")
    public PagedResponse<CustomerResponse> listCustomers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(defaultValue = "createdAt") String sortBy,
            @RequestParam(defaultValue = "DESC") String sortDir
    ) {
        Sort sort = sortDir.equalsIgnoreCase("ASC")
                ? Sort.by(sortBy).ascending()
                : Sort.by(sortBy).descending();
        return customerService.listCustomers(PageRequest.of(page, size, sort));
    }

    @GetMapping("/kyc-status/{status}")
    @PreAuthorize("hasRole('ROLE_BANK_STAFF') or hasRole('ROLE_ADMIN')")
    public PagedResponse<CustomerResponse> listByKycStatus(
            @PathVariable KycStatus status,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return customerService.listCustomersByKycStatus(status, PageRequest.of(page, size));
    }

    @GetMapping("/search")
    @PreAuthorize("hasRole('ROLE_BANK_STAFF') or hasRole('ROLE_ADMIN')")
    public PagedResponse<CustomerResponse> search(
            @RequestParam String q,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size
    ) {
        return customerService.searchCustomers(q, PageRequest.of(page, size));
    }

    // -----------------------------------------------------------------------
    // Profile Update
    // -----------------------------------------------------------------------

    @PatchMapping("/{id}")
    @PreAuthorize("hasRole('ROLE_CUSTOMER') or hasRole('ROLE_BANK_STAFF') or hasRole('ROLE_ADMIN')")
    public CustomerResponse updateProfile(
            @PathVariable UUID id,
            @Valid @RequestBody CustomerProfileUpdateRequest request
    ) {
        return customerService.updateProfile(id, request);
    }

    // -----------------------------------------------------------------------
    // KYC Management
    // -----------------------------------------------------------------------

    @PatchMapping("/{id}/kyc")
    @PreAuthorize("hasRole('ROLE_BANK_STAFF') or hasRole('ROLE_ADMIN')")
    public CustomerResponse updateKycStatus(
            @PathVariable UUID id,
            @Valid @RequestBody KycUpdateRequest request
    ) {
        log.info("PATCH /v1/customers/{}/kyc - newStatus={}", id, request.getNewStatus());
        return customerService.updateKycStatus(id, request);
    }

    // -----------------------------------------------------------------------
    // Deactivation
    // -----------------------------------------------------------------------

    @DeleteMapping("/{id}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ROLE_ADMIN')")
    public void deactivateCustomer(@PathVariable UUID id) {
        log.info("DELETE /v1/customers/{} - deactivating", id);
        customerService.deactivateCustomer(id);
    }

    // -----------------------------------------------------------------------
    // Address Management
    // -----------------------------------------------------------------------

    @PostMapping("/{customerId}/addresses")
    @ResponseStatus(HttpStatus.CREATED)
    @PreAuthorize("hasRole('ROLE_CUSTOMER') or hasRole('ROLE_BANK_STAFF') or hasRole('ROLE_ADMIN')")
    public AddressResponse addAddress(
            @PathVariable UUID customerId,
            @Valid @RequestBody AddressRequest request
    ) {
        return customerService.addAddress(customerId, request);
    }

    @PutMapping("/{customerId}/addresses/{addressId}")
    @PreAuthorize("hasRole('ROLE_CUSTOMER') or hasRole('ROLE_BANK_STAFF') or hasRole('ROLE_ADMIN')")
    public AddressResponse updateAddress(
            @PathVariable UUID customerId,
            @PathVariable UUID addressId,
            @Valid @RequestBody AddressRequest request
    ) {
        return customerService.updateAddress(customerId, addressId, request);
    }

    @DeleteMapping("/{customerId}/addresses/{addressId}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    @PreAuthorize("hasRole('ROLE_CUSTOMER') or hasRole('ROLE_BANK_STAFF') or hasRole('ROLE_ADMIN')")
    public void removeAddress(
            @PathVariable UUID customerId,
            @PathVariable UUID addressId
    ) {
        customerService.removeAddress(customerId, addressId);
    }
}