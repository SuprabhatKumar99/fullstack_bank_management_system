package com.cbs.account_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.math.BigDecimal;
import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.context.annotation.Import;
import org.springframework.http.MediaType;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.JwtRequestPostProcessor;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.cbs.account_service.config.SecurityConfig;
import com.cbs.account_service.dto.request.CloseAccountRequest;
import com.cbs.account_service.dto.request.OpenAccountRequest;
import com.cbs.account_service.dto.response.AccountResponse;
import com.cbs.account_service.dto.response.BalanceResponse;
import com.cbs.account_service.enums.AccountStatus;
import com.cbs.account_service.enums.AccountType;
import com.cbs.account_service.exception.AccountNotFoundException;
import com.cbs.account_service.exception.AccountOperationException;
import com.cbs.account_service.service.AccountService;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(AccountController.class)
@Import(SecurityConfig.class)
@DisplayName("AccountController")
class AccountControllerTest {

    @Autowired MockMvc      mockMvc;
    private final ObjectMapper objectMapper = new ObjectMapper();
    @MockitoBean AccountService accountService;
    @MockitoBean JwtDecoder jwtDecoder;

    private final UUID accountId  = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID branchId   = UUID.randomUUID();

    // ── Open Account ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/accounts — 201 for valid SAVINGS request")
    void openAccount_valid_returns201() throws Exception {
        OpenAccountRequest req = buildOpenRequest(AccountType.SAVINGS);
        AccountResponse resp = buildAccountResponse();

        given(accountService.openAccount(any(), any())).willReturn(resp);

        mockMvc.perform(post("/api/v1/accounts")
                .with(jwtWithRole("TELLER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.accountId").value(accountId.toString()))
            .andExpect(jsonPath("$.data.accountType").value("SAVINGS"));
    }

    @Test
    @DisplayName("POST /api/v1/accounts — 400 when customerId missing")
    void openAccount_missingCustomerId_returns400() throws Exception {
        OpenAccountRequest req = buildOpenRequest(AccountType.SAVINGS);
        req.setCustomerId(null);

        mockMvc.perform(post("/api/v1/accounts")
                .with(jwtWithRole("TELLER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.error.details.customerId").exists());
    }

    @Test
    @DisplayName("POST /api/v1/accounts — 403 for CUSTOMER role")
    void openAccount_customerRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/accounts")
                .with(jwtWithRole("CUSTOMER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildOpenRequest(AccountType.SAVINGS))))
            .andExpect(status().isForbidden());
    }

    // ── Balance ───────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/balance — 200 with balance data")
    void getBalance_active_returns200() throws Exception {
        BalanceResponse balResp = BalanceResponse.builder()
            .accountId(accountId)
            .accountNumber("CBS00000001001")
            .currency("INR")
            .balance(new BigDecimal("10000.00"))
            .holdAmount(BigDecimal.ZERO)
            .overdraftLimit(BigDecimal.ZERO)
            .availableBalance(new BigDecimal("10000.00"))
            .isFrozen(false)
            .status("ACTIVE")
            .build();

        given(accountService.getBalance(accountId)).willReturn(balResp);

        mockMvc.perform(get("/api/v1/accounts/{id}/balance", accountId)
                .with(jwtWithRole("TELLER")))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.balance").value(10000.00))
            .andExpect(jsonPath("$.data.availableBalance").value(10000.00))
            .andExpect(jsonPath("$.data.isFrozen").value(false));
    }

    @Test
    @DisplayName("GET /api/v1/accounts/{id}/balance — 422 for CLOSED account")
    void getBalance_closed_returns422() throws Exception {
        given(accountService.getBalance(accountId))
            .willThrow(new AccountOperationException("Account is CLOSED"));

        mockMvc.perform(get("/api/v1/accounts/{id}/balance", accountId)
                .with(jwtWithRole("TELLER")))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("ACCOUNT_OPERATION_ERROR"));
    }

    // ── Close ─────────────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/accounts/{id}/close — 200 on success")
    void closeAccount_zeroBalance_returns200() throws Exception {
        CloseAccountRequest req = new CloseAccountRequest();
        req.setReason("Customer request");

        AccountResponse resp = buildAccountResponse();
        resp.setStatus(AccountStatus.CLOSED);

        given(accountService.closeAccount(any(), any())).willReturn(resp);

        mockMvc.perform(post("/api/v1/accounts/{id}/close", accountId)
                .with(jwtWithRole("TELLER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$.data.status").value("CLOSED"));
    }

    @Test
    @DisplayName("POST /api/v1/accounts/{id}/close — 422 when balance non-zero")
    void closeAccount_nonZeroBalance_returns422() throws Exception {
        CloseAccountRequest req = new CloseAccountRequest();
        req.setReason("Customer request");

        given(accountService.closeAccount(any(), any()))
            .willThrow(new AccountOperationException("Cannot close with non-zero balance"));

        mockMvc.perform(post("/api/v1/accounts/{id}/close", accountId)
                .with(jwtWithRole("TELLER"))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(req)))
            .andExpect(status().isUnprocessableEntity())
            .andExpect(jsonPath("$.error.code").value("ACCOUNT_OPERATION_ERROR"));
    }

    // ── Not Found ─────────────────────────────────────────────────

    @Test
    @DisplayName("GET /api/v1/accounts/{id} — 404 when not found")
    void getAccount_notFound_returns404() throws Exception {
        given(accountService.getById(accountId))
            .willThrow(new AccountNotFoundException("Account not found: " + accountId));

        mockMvc.perform(get("/api/v1/accounts/{id}", accountId)
                .with(jwtWithRole("TELLER")))
            .andExpect(status().isNotFound())
            .andExpect(jsonPath("$.error.code").value("ACCOUNT_NOT_FOUND"));
    }

    // ── Helpers ───────────────────────────────────────────────────

    private OpenAccountRequest buildOpenRequest(AccountType type) {
        OpenAccountRequest req = new OpenAccountRequest();
        req.setCustomerId(customerId);
        req.setBranchId(branchId);
        req.setAccountType(type);
        req.setCurrency("INR");
        return req;
    }

    private JwtRequestPostProcessor jwtWithRole(String role) {
        return jwt()
            .jwt(j -> j.subject(UUID.randomUUID().toString())
                       .claim("roles", java.util.List.of(role)))
            .authorities(new SimpleGrantedAuthority("ROLE_" + role));
    }

    private AccountResponse buildAccountResponse() {
        return AccountResponse.builder()
            .accountId(accountId)
            .accountNumber("CBS00000001001")
            .customerId(customerId)
            .branchId(branchId)
            .accountType(AccountType.SAVINGS)
            .currency("INR")
            .balance(BigDecimal.ZERO)
            .holdAmount(BigDecimal.ZERO)
            .overdraftLimit(BigDecimal.ZERO)
            .availableBalance(BigDecimal.ZERO)
            .minimumBalance(new BigDecimal("1000.00"))
            .status(AccountStatus.ACTIVE)
            .isFrozen(false)
            .build();
    }
}
