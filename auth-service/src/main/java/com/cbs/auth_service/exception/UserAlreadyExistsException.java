package com.cbs.auth_service.exception;


/**
 * Thrown when a registration attempt uses an already-taken username or email.
 * Maps to HTTP 409 Conflict.
 */
public class UserAlreadyExistsException extends RuntimeException {
    public UserAlreadyExistsException(String message) {
        super(message);
    }
}