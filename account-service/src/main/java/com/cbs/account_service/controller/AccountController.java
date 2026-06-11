package com.cbs.account_service.controller;

import java.math.BigDecimal;
import java.util.List;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.cbs.account_service.dto.request.CloseAccountRequest;
import com.cbs.account_service.dto.request.OpenAccountRequest;
import com.cbs.account_service.dto.request.UpdateAccountRequest;
import com.cbs.account_service.dto.response.AccountResponse;
import com.cbs.account_service.dto.response.AccountSummaryResponse;
import com.cbs.account_service.dto.response.ApiResponse;
import com.cbs.account_service.dto.response.BalanceResponse;
import com.cbs.account_service.enums.AccountStatus;
import com.cbs.account_service.service.AccountService;

import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * REST controller for Account Service.
 *
 * Base URL : /api/v1/accounts
 * Port     : 8082
 *
 * Endpoint summary:
 * ┌──────────────────────────────────────────────────────────────────────────┐
 * │ POST   /                            Open a new account                  │
 * │ GET    /{accountId}                 Get full account details             │
 * │ GET    /number/{accountNumber}      Lookup by account number             │
 * │ GET    /{accountId}/balance         Balance enquiry (Redis cached)       │
 * │ GET    /customer/{customerId}       All accounts for a customer          │
 * │ GET    /branch/{branchId}           All accounts for a branch            │
 * │ PATCH  /{accountId}                 Update mutable fields                │
 * │ POST   /{accountId}/activate        Activate a pending account           │
 * │ POST   /{accountId}/freeze          Quick-freeze (fraud)                 │
 * │ POST   /{accountId}/unfreeze        Remove quick-freeze                  │
 * │ POST   /{accountId}/close           Close account                        │
 * │ GET    /{customerId}/total-balance  Aggregate balance across accounts    │
 * └──────────────────────────────────────────────────────────────────────────┘
 */
@RestController
@RequestMapping("/api/v1/accounts")
@RequiredArgsConstructor
@Slf4j
public class AccountController {

    private final AccountService accountService;

    // ─────────────────────────────────────────────────────────────
    // OPEN ACCOUNT
    // ─────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/accounts
     *
     * Opens a new bank account. Returns 201 with the full account profile.
     *
     * Example — open savings account:
     * {
     *   "customerId":   "uuid",
     *   "branchId":     "uuid",
     *   "accountType":  "SAVINGS",
     *   "currency":     "INR",
     *   "nomineeName":  "Priya Sharma",
     *   "nomineeRelation": "SPOUSE"
     * }
     *
     * Example — open FD:
     * {
     *   "customerId":       "uuid",
     *   "branchId":         "uuid",
     *   "accountType":      "FIXED_DEPOSIT",
     *   "tenureMonths":     12,
     *   "initialDeposit":   50000.00,
     *   "parentAccountId":  "uuid-of-savings-account"
     * }
     */
    @PostMapping
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> openAccount(
            @Valid @RequestBody OpenAccountRequest request,
            @AuthenticationPrincipal Jwt jwt) {

        UUID staffId = UUID.fromString(jwt.getSubject());
        AccountResponse response = accountService.openAccount(request, staffId);

        return ResponseEntity
                .status(HttpStatus.CREATED)
                .body(ApiResponse.ok(response));
    }

    // ─────────────────────────────────────────────────────────────
    // READ — SINGLE ACCOUNT
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/accounts/{accountId}
     *
     * Full account details including all balance fields, nominee, and audit info.
     * Response served from Redis cache (10-min TTL).
     */
    @GetMapping("/{accountId}")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN') or " +
                  "@accountService.getById(#accountId).customerId.toString() " +
                  "== authentication.name")
    public ResponseEntity<ApiResponse<AccountResponse>> getAccount(
            @PathVariable UUID accountId) {

        return ResponseEntity.ok(ApiResponse.ok(
                accountService.getById(accountId)));
    }

    /**
     * GET /api/v1/accounts/number/{accountNumber}
     *
     * Lookup by human-readable account number (e.g. CBS00000001001).
     * Used by the teller UI and ATM switch.
     */
    @GetMapping("/number/{accountNumber}")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN', 'ATM_SWITCH', 'PAYMENT_SERVICE')")
    public ResponseEntity<ApiResponse<AccountResponse>> getByAccountNumber(
            @PathVariable String accountNumber) {

        return ResponseEntity.ok(ApiResponse.ok(
                accountService.getByAccountNumber(accountNumber)));
    }

    // ─────────────────────────────────────────────────────────────
    // BALANCE ENQUIRY
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/accounts/{accountId}/balance
     *
     * Lightweight balance response served from Redis cache.
     * Cache miss → DB read → cache write → return.
     * Cache hit  → Redis read only (sub-millisecond).
     *
     * Includes: balance, holdAmount, overdraftLimit, availableBalance,
     *           isFrozen, status, cachedAt timestamp.
     */
    @GetMapping("/{accountId}/balance")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN', 'ATM_SWITCH', 'PAYMENT_SERVICE') or " +
                  "@accountService.getById(#accountId).customerId.toString() " +
                  "== authentication.name")
    public ResponseEntity<ApiResponse<BalanceResponse>> getBalance(
            @PathVariable UUID accountId) {

        return ResponseEntity.ok(ApiResponse.ok(
                accountService.getBalance(accountId)));
    }

    // ─────────────────────────────────────────────────────────────
    // READ — LISTS
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/accounts/customer/{customerId}
     *
     * All accounts for a customer — used on the teller dashboard.
     * Returns summary projections (not full detail) for performance.
     * Cached in Redis (10-min TTL); evicted when any account changes.
     */
    @GetMapping("/customer/{customerId}")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN') or " +
                  "#customerId.toString() == authentication.name")
    public ResponseEntity<ApiResponse<List<AccountSummaryResponse>>> getAccountsByCustomer(
            @PathVariable UUID customerId) {

        return ResponseEntity.ok(ApiResponse.ok(
                accountService.getAccountsByCustomer(customerId)));
    }

    /**
     * GET /api/v1/accounts/branch/{branchId}?page=0&size=20
     *
     * Paginated account list for a branch.
     * Used by branch managers and reporting jobs.
     */
    @GetMapping("/branch/{branchId}")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<Page<AccountSummaryResponse>>> getAccountsByBranch(
            @PathVariable UUID branchId,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok(
                accountService.getAccountsByBranch(branchId, pageable)));
    }

    /**
     * GET /api/v1/accounts?status=DORMANT&page=0&size=20
     *
     * Filter accounts by status. Used by operations team.
     */
    @GetMapping
    @PreAuthorize("hasRole('ADMIN')")
    public ResponseEntity<ApiResponse<Page<AccountSummaryResponse>>> getAccountsByStatus(
            @RequestParam(defaultValue = "ACTIVE") AccountStatus status,
            @PageableDefault(size = 20) Pageable pageable) {

        return ResponseEntity.ok(ApiResponse.ok(
                accountService.getAccountsByStatus(status, pageable)));
    }

    // ─────────────────────────────────────────────────────────────
    // UPDATE
    // ─────────────────────────────────────────────────────────────

    /**
     * PATCH /api/v1/accounts/{accountId}
     *
     * Update mutable fields: nominee details, overdraft limit.
     * Immutable fields (accountNumber, type, currency, customer) are ignored.
     *
     * Example:
     * {
     *   "nomineeName":     "Priya Sharma",
     *   "nomineeRelation": "SPOUSE",
     *   "overdraftLimit":  100000.00
     * }
     */
    @PatchMapping("/{accountId}")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> updateAccount(
            @PathVariable UUID accountId,
            @Valid @RequestBody UpdateAccountRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(
                accountService.updateAccount(accountId, request)));
    }

    // ─────────────────────────────────────────────────────────────
    // LIFECYCLE OPERATIONS
    // ─────────────────────────────────────────────────────────────

    /**
     * POST /api/v1/accounts/{accountId}/activate
     *
     * Activates a PENDING_ACTIVATION account (e.g. after FD funding confirmed).
     */
    @PostMapping("/{accountId}/activate")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> activateAccount(
            @PathVariable UUID accountId) {

        return ResponseEntity.ok(ApiResponse.ok(
                accountService.activateAccount(accountId)));
    }

    /**
     * POST /api/v1/accounts/{accountId}/freeze
     *
     * Quick-freeze: sets isFrozen=true, blocks all debits and credits.
     * Used by Fraud Detection Service for immediate holds.
     * Does NOT change account status (reversible via /unfreeze).
     */
    @PostMapping("/{accountId}/freeze")
    @PreAuthorize("hasAnyRole('FRAUD_ANALYST', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> freezeAccount(
            @PathVariable UUID accountId,
            @RequestParam String reason) {

        return ResponseEntity.ok(ApiResponse.ok(
                accountService.freezeAccount(accountId, reason)));
    }

    /**
     * POST /api/v1/accounts/{accountId}/unfreeze
     *
     * Removes the quick-freeze flag. Account resumes normal operation.
     */
    @PostMapping("/{accountId}/unfreeze")
    @PreAuthorize("hasAnyRole('FRAUD_ANALYST', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> unfreezeAccount(
            @PathVariable UUID accountId) {

        return ResponseEntity.ok(ApiResponse.ok(
                accountService.unfreezeAccount(accountId)));
    }

    /**
     * POST /api/v1/accounts/{accountId}/close
     *
     * Permanently closes an account.
     * Pre-conditions: balance must be zero, account must not already be closed.
     *
     * {
     *   "reason": "Customer requested account closure"
     * }
     */
    @PostMapping("/{accountId}/close")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN')")
    public ResponseEntity<ApiResponse<AccountResponse>> closeAccount(
            @PathVariable UUID accountId,
            @Valid @RequestBody CloseAccountRequest request) {

        return ResponseEntity.ok(ApiResponse.ok(
                accountService.closeAccount(accountId, request)));
    }

    // ─────────────────────────────────────────────────────────────
    // BALANCE AGGREGATES
    // ─────────────────────────────────────────────────────────────

    /**
     * GET /api/v1/accounts/customer/{customerId}/total-balance
     *
     * Sum of balances across all ACTIVE accounts for a customer.
     * Used by the teller dashboard header and wealth overview.
     */
    @GetMapping("/customer/{customerId}/total-balance")
    @PreAuthorize("hasAnyRole('TELLER', 'ADMIN') or " +
                  "#customerId.toString() == authentication.name")
    public ResponseEntity<ApiResponse<BigDecimal>> getTotalBalance(
            @PathVariable UUID customerId) {

        return ResponseEntity.ok(ApiResponse.ok(
                accountService.getTotalBalanceByCustomer(customerId)));
    }
}