package com.cbs.account_service.exception;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** Business rule violation — account cannot perform the requested operation. */
@ResponseStatus(HttpStatus.UNPROCESSABLE_ENTITY)
public class AccountOperationException extends RuntimeException {
    public AccountOperationException(String msg) { super(msg); }
}