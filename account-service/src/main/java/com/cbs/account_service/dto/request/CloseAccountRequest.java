package com.cbs.account_service.dto.request;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/** POST /api/v1/accounts/{accountId}/close */
@Data
public class CloseAccountRequest {

    @NotBlank(message = "Closure reason is required")
    @Size(max = 200)
    private String reason;
}