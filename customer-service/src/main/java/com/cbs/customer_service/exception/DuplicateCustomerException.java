package com.cbs.customer_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/**
 * Thrown when a registration attempt violates a unique constraint
 * (duplicate phone, email, or PAN).
 */
@ResponseStatus(HttpStatus.CONFLICT)
public class DuplicateCustomerException extends RuntimeException {
    public DuplicateCustomerException(String message) {
        super(message);
    }
}