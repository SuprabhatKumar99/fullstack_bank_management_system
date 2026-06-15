package com.cbs.customer_service.exception;


public class InvalidKycTransitionException extends RuntimeException {

    public InvalidKycTransitionException(String message) {
        super(message);
    }

    public InvalidKycTransitionException(String message, Throwable cause) {
        super(message, cause);
    }
}