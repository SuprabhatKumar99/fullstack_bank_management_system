package com.cbs.customer_service.dto.response;

import java.util.UUID;

import com.cbs.customer_service.enums.CustomerType;
import com.cbs.customer_service.enums.KycStatus;
import com.fasterxml.jackson.annotation.JsonInclude;

import lombok.Builder;
import lombok.Data;

/**
 * Lightweight projection used in list/search results.
 * Avoids returning full profile data for bulk queries.
 */
@Data
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CustomerSummaryResponse {
    private UUID         customerId;
    private CustomerType customerType;
    private String       displayName;
    private String       phone;
    private String       email;
    private String       panNumber;
    private KycStatus    kycStatus;
    private String       city;
    private String       state;
}