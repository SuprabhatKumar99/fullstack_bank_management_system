package com.cbs.auth_service.exception;

import org.springframework.web.bind.annotation.ResponseStatus;

// @ResponseStatus(HttpS)
public class TokenReuseException extends RuntimeException {
    public TokenReuseException(String message){
        super(message);
    }
}
