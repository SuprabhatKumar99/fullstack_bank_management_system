package com.cbs.account_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

import java.math.BigDecimal;
import java.util.UUID;

/** Thrown when an account has insufficient available balance to debit. */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class InsufficientFundsException extends RuntimeException {
    public InsufficientFundsException(UUID accountId,
                                      BigDecimal requested,
                                      BigDecimal available) {
        super(String.format(
            "Insufficient funds in account %s. Requested: %s, Available: %s",
            accountId, requested, available));
    }
}