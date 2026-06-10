package com.cbs.customer_service.exception;

import com.cbs.customer_service.enums.KycStatus;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a KYC status transition is not permitted by the state machine.
 * E.g. trying to VERIFY a customer who is already VERIFIED.
 */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InvalidKycTransitionException extends RuntimeException {
    public InvalidKycTransitionException(KycStatus from, KycStatus to) {
        super("Invalid KYC transition: " + from + " → " + to);
    }
}