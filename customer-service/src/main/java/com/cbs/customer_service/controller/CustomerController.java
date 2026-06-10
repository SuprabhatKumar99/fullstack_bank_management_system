package com.cbs.customer_service.controller;


import java.util.UUID;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cbs.customer_service.dto.request.KycStatusUpdateRequest;
import com.cbs.customer_service.dto.request.RegisterCustomerRequest;
import com.cbs.customer_service.dto.request.UpdateProfileRequest;
import com.cbs.customer_service.dto.response.ApiResponse;
import com.cbs.customer_service.dto.response.CustomerResponse;
import com.cbs.customer_service.dto.response.CustomerSummaryResponse;
import com.cbs.customer_service.enums.KycStatus;
import com.cbs.customer_service.service.CustomerService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for Customer Service.
 *
 * Base URL : /api/v1/customers
 * Auth     : JWT Bearer token (validated by API Gateway before reaching this service).
 *            Roles: ROLE_TELLER, ROLE_KYC_OFFICER, ROLE_ADMIN, ROLE_CUSTOMER
 *
 * Endpoint summary:
 * ┌─────────────────────────────────────────────────────────────────────┐
 * │ POST   /                           Register new customer            │
 * │ GET    /{customerId}               Get full profile by ID           │
 * │ GET    /by-phone?phone=            Lookup by phone                  │
 * │ GET    /by-pan?pan=                Lookup by PAN                    │
 * │ GET    /search?firstName=&lastName= Name search (teller UI)         │
 * │ PATCH  /{customerId}/profile       Update mutable profile fields    │
 * │ PATCH  /{customerId}/kyc           Update KYC status (officer only) │
 * │ GET    /kyc/pending                List customers pending KYC       │
 * └─────────────────────────────────────────────────────────────────────┘
 */
@RestController
@RequestMapping("/api/v1/customers")
@RequiredArgsConstructor
@Slf4j
public class CustomerController {

    @Autowired CustomerService customerService;

    // ─────────────────────────────────────────────────────────────
    // REGISTRATION
    // ─────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/customers
     *
     * Registers a new customer. Accessible by branch tellers and admin.
     * Returns 201 Created with the full customer profile.
     *
     * Example request:
     * {
     *   "customerType": "INDIVIDUAL",
     *   "firstName": "Ravi",
     *   "lastName": "Sharma",
     *   "dateOfBirth": "1990-05-15",
     *   "phone": "9876543210",
     *   "email": "ravi.sharma@email.com",
     *   "panNumber": "ABCDE1234F",
     *   "addressLine1": "42 MG Road",
     *   "city": "Bengaluru",
     *   "state": "Karnataka",
     *   "pincode": "560001",
     *   "homeBranchId": "uuid-of-branch"
     * }
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CustomerResponse>> registerCustomer(
            @Valid @RequestBody RegisterCustomerRequest request,
            @AuthenticationPrincipal(expression = "subject") String staffSubject) {

        UUID staffId = UUID.fromString(staffSubject);
        CustomerResponse response = customerService.registerCustomer(request, staffId);

        return ResponseEntity
            .status(HttpStatus.CREATED)
            .body(ApiResponse.ok(response));
    }

    // ─────────────────────────────────────────────────────────────
    // PROFILE READS
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/customers/{customerId}
     *
     * Full profile. Accessible by tellers (any customer) and the customer
     * themselves (only their own profile).
     */
    @GetMapping("/{customerId}")
    @PreAuthorize("hasAnyRole('TELLER', 'KYC_OFFICER', 'ADMIN') or " +
                  "#customerId.toString() == authentication.name")
    public ResponseEntity<ApiResponse<CustomerResponse>> getCustomer(
            @PathVariable UUID customerId) {

        return ResponseEntity.ok(ApiResponse.ok(
            customerService.getById(customerId)));
    }

    /**
     * GET /api/v1/customers/by-phone?phone=9876543210
     *
     * Phone lookup — used by the teller UI quick-search bar.
     */
    @GetMapping("/by-phone")
    @PreAuthorize("hasAnyRole('TELLER', 'KYC_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CustomerResponse>> getByPhone(
            @RequestParam String phone) {

        return ResponseEntity.ok(ApiResponse.ok(
            customerService.getByPhone(phone)));
    }

    /**
     * GET /api/v1/customers/by-pan?pan=ABCDE1234F
     *
     * PAN lookup — used for KYC dedup check and account opening.
     */
    @GetMapping("/by-pan")
    @PreAuthorize("hasAnyRole('TELLER', 'KYC_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<CustomerResponse>> getByPan(
            @RequestParam String pan) {

        return ResponseEntity.ok(ApiResponse.ok(
            customerService.getByPan(pan)));
    }

    /**
     * GET /api/v1/customers/search?firstName=Ravi&lastName=Sharma&page=0&size=20
     *
     * Name-based search for the teller UI.
     * Returns a paginated list of CustomerSummaryResponse.
     */
    @GetMapping("/search")
    @PreAuthorize("hasAnyRole('TELLER', 'KYC_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<CustomerSummaryResponse>>> search(
            @RequestParam(required = false) String firstName,
            @RequestParam(required = false) String lastName,
            @PageableDefault(size = 20, sort = "lastName") Pageable pageable) {

        Page<CustomerSummaryResponse> results =
            customerService.searchByName(firstName, lastName, pageable);

        return ResponseEntity.ok(ApiResponse.ok(results));
    }

    // ─────────────────────────────────────────────────────────────
    // PROFILE UPDATE
    // ─────────────────────────────────────────────────────────────

    /**
     * PATCH /api/v1/customers/{customerId}/profile
     *
     * Partial update — only send fields you want to change.
     * Immutable fields (phone, PAN, customerType) are silently ignored.
     *
     * Example request:
     * {
     *   "email": "new.email@example.com",
     *   "addressLine1": "New Address",
     *   "city": "Mumbai"
     * }
     */
    @PatchMapping("/{customerId}/profile")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN') or " +
                  "#customerId.toString() == authentication.name")
    public ResponseEntity<ApiResponse<CustomerResponse>> updateProfile(
            @PathVariable UUID customerId,
            @Valid @RequestBody UpdateProfileRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(
            customerService.updateProfile(customerId, request)));
    }

    // ─────────────────────────────────────────────────────────────
    // KYC MANAGEMENT
    // ─────────────────────────────────────────────────────────────

    /**
     * PATCH /api/v1/customers/{customerId}/kyc
     *
     * Updates KYC status. Restricted to KYC officers and admins.
     * The service enforces the state machine — invalid transitions return 422.
     *
     * Example — approve KYC:
     * {
     *   "status": "VERIFIED",
     *   "expiresAt": "2027-06-08T00:00:00+05:30"
     * }
     *
     * Example — reject KYC:
     * {
     *   "status": "REJECTED",
     *   "rejectionReason": "PAN document is blurred and unreadable"
     * }
     */
    @PatchMapping("/{customerId}/kyc")
    @PreAuthorize("hasAnyRole('KYC_OFFICER', 'ADMIN')")
        public ResponseEntity<ApiResponse<CustomerResponse>> updateKycStatus(
            @PathVariable UUID customerId,
            @Valid @RequestBody KycStatusUpdateRequest request,
            @AuthenticationPrincipal(expression = "subject") String officerId) {
        return ResponseEntity.ok(ApiResponse.ok(
            customerService.updateKycStatus(customerId, request, officerId)));
    }

    /**
     * GET /api/v1/customers/kyc/pending?page=0&size=20
     *
     * Lists customers pending KYC review — for the KYC officer dashboard.
     * Can filter by status: PENDING, UNDER_REVIEW, REJECTED, EXPIRED.
     */
    @GetMapping("/kyc/pending")
    @PreAuthorize("hasAnyRole('KYC_OFFICER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<CustomerSummaryResponse>>> listByKycStatus(
            @RequestParam(defaultValue = "UNDER_REVIEW") KycStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok(
            customerService.listByKycStatus(status, pageable)));
    }
}