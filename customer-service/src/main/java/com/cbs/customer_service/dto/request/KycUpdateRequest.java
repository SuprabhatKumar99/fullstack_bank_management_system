package com.cbs.customer_service.dto.request;

import com.cbs.customer_service.enums.KycStatus;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;//bolo nunu  . tumhe dekhne aay h  ham soche tum bhaiya ke sath ho kii baat h badka bhaiya ke samne itna sarif ??? KWAIT AATE H 
//KAHA SE AATE H? KITNA TIME?? Ae meethi

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class KycUpdateRequest {

    @NotNull(message = "New KYC status is required")
    private KycStatus newStatus;

    @Size(max = 500, message = "Rejection reason must not exceed 500 characters")
    private String rejectionReason;
}