package com.cbs.customer_service.controller;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.jwt;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.util.UUID;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
// import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;

// import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.bean.override.mockito.MockitoBean;

import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;

import com.cbs.customer_service.dto.request.RegisterCustomerRequest;
import com.cbs.customer_service.dto.response.CustomerResponse;
import com.cbs.customer_service.enums.CustomerType;
import com.cbs.customer_service.enums.KycStatus;
import com.cbs.customer_service.exception.DuplicateCustomerException;
import com.cbs.customer_service.service.CustomerService;
import com.fasterxml.jackson.databind.ObjectMapper;

@WebMvcTest(CustomerController.class)
@DisplayName("CustomerController")
class CustomerControllerTest {

    @Autowired MockMvc     mockMvc;
    @Autowired ObjectMapper objectMapper;
    @MockitoBean  CustomerService customerService;

    private final UUID customerId = UUID.randomUUID();

    // ── Registration ──────────────────────────────────────────────

    @Test
    @DisplayName("POST /api/v1/customers — 201 for valid INDIVIDUAL request")
    void registerCustomer_validRequest_returns201() throws Exception {
        RegisterCustomerRequest request = buildValidRequest();

        CustomerResponse response = CustomerResponse.builder()
            .customerId(customerId)
            .customerType(CustomerType.INDIVIDUAL)
            .firstName("Ravi")
            .lastName("Sharma")
            .kycStatus(KycStatus.PENDING)
            .build();

        given(customerService.registerCustomer(any(), any())).willReturn(response);

        mockMvc.perform(post("/api/v1/customers")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                                      .claim("roles", java.util.List.of("TELLER"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isCreated())
            .andExpect(jsonPath("$.success").value(true))
            .andExpect(jsonPath("$.data.customerId").value(customerId.toString()))
            .andExpect(jsonPath("$.data.kycStatus").value("PENDING"));
    }

    @Test
    @DisplayName("POST /api/v1/customers — 400 when phone is missing")
    void registerCustomer_missingPhone_returns400() throws Exception {
        RegisterCustomerRequest request = buildValidRequest();
        request.setPhone(null);

        mockMvc.perform(post("/api/v1/customers")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                                      .claim("roles", java.util.List.of("TELLER"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(request)))
            .andExpect(status().isBadRequest())
            .andExpect(jsonPath("$.success").value(false))
            .andExpect(jsonPath("$.error.code").value("VALIDATION_FAILED"))
            .andExpect(jsonPath("$.error.details.phone").exists());
    }

    @Test
    @DisplayName("POST /api/v1/customers — 409 on duplicate phone")
    void registerCustomer_duplicatePhone_returns409() throws Exception {
        given(customerService.registerCustomer(any(), any()))
            .willThrow(new DuplicateCustomerException("Phone already exists"));

        mockMvc.perform(post("/api/v1/customers")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                                      .claim("roles", java.util.List.of("TELLER"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildValidRequest())))
            .andExpect(status().isConflict())
            .andExpect(jsonPath("$.error.code").value("DUPLICATE_CUSTOMER"));
    }

    @Test
    @DisplayName("POST /api/v1/customers — 403 when caller has ROLE_CUSTOMER only")
    void registerCustomer_insufficientRole_returns403() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                .with(jwt().jwt(j -> j.subject(UUID.randomUUID().toString())
                                      .claim("roles", java.util.List.of("CUSTOMER"))))
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildValidRequest())))
            .andExpect(status().isForbidden());
    }

    @Test
    @DisplayName("POST /api/v1/customers — 401 with no JWT")
    void registerCustomer_noAuth_returns401() throws Exception {
        mockMvc.perform(post("/api/v1/customers")
                .contentType(MediaType.APPLICATION_JSON)
                .content(objectMapper.writeValueAsString(buildValidRequest())))
            .andExpect(status().isUnauthorized());
    }

    // ── Helpers ───────────────────────────────────────────────────

    private RegisterCustomerRequest buildValidRequest() {
        RegisterCustomerRequest req = new RegisterCustomerRequest();
        req.setCustomerType(CustomerType.INDIVIDUAL);
        req.setFirstName("Ravi");
        req.setLastName("Sharma");
        req.setPhone("9876543210");
        req.setEmail("ravi@example.com");
        req.setPanNumber("ABCDE1234F");
        req.setDateOfBirth(java.time.LocalDate.of(1990, 5, 15));
        return req;
    }
}