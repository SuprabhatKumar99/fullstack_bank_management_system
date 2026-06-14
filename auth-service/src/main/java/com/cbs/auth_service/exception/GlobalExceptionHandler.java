package com.cbs.auth_service.exception;

import lombok.extern.slf4j.Slf4j;
import org.springframework.http.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.context.request.WebRequest;

import java.time.Instant;
import java.util.HashMap;
import java.util.Map;

/**
 * Centralised exception-to-HTTP mapping for the Auth Service.
 *
 * <p>All error responses share the same envelope shape:
 * <pre>
 * {
 *   "timestamp": "2024-01-15T10:30:00Z",
 *   "status":    401,
 *   "error":     "Unauthorized",
 *   "message":   "Invalid username/email or password",
 *   "path":      "/api/auth/login"
 * }
 * </pre>
 */
@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── 409 Conflict ──────────────────────────────────────────

    @ExceptionHandler(UserAlreadyExistsException.class)
    public ResponseEntity<ErrorResponse> handleUserAlreadyExists(
            UserAlreadyExistsException ex, WebRequest request) {
        return buildResponse(HttpStatus.CONFLICT, ex.getMessage(), request);
    }

    // ── 401 Unauthorized ──────────────────────────────────────

    @ExceptionHandler(InvalidCredentialsException.class)
    public ResponseEntity<ErrorResponse> handleInvalidCredentials(
            InvalidCredentialsException ex, WebRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(InvalidTokenException.class)
    public ResponseEntity<ErrorResponse> handleInvalidToken(
            InvalidTokenException ex, WebRequest request) {
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    @ExceptionHandler(TokenReuseException.class)
    public ResponseEntity<ErrorResponse> handleTokenReuse(
            TokenReuseException ex, WebRequest request) {
        log.warn("Token reuse detected: {}", ex.getMessage());
        return buildResponse(HttpStatus.UNAUTHORIZED, ex.getMessage(), request);
    }

    // ── 423 Locked ────────────────────────────────────────────

    @ExceptionHandler(AccountLockedException.class)
    public ResponseEntity<ErrorResponse> handleAccountLocked(
            AccountLockedException ex, WebRequest request) {
        return buildResponse(HttpStatus.LOCKED, ex.getMessage(), request);
    }

    // ── 403 Forbidden ─────────────────────────────────────────

    @ExceptionHandler(AccessDeniedException.class)
    public ResponseEntity<ErrorResponse> handleAccessDenied(
            AccessDeniedException ex, WebRequest request) {
        return buildResponse(HttpStatus.FORBIDDEN, "Access denied", request);
    }

    // ── 400 Validation ────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ValidationErrorResponse> handleValidation(
            MethodArgumentNotValidException ex, WebRequest request) {

        Map<String, String> fieldErrors = new HashMap<>();
        for (FieldError fe : ex.getBindingResult().getFieldErrors()) {
            fieldErrors.put(fe.getField(), fe.getDefaultMessage());
        }

        ValidationErrorResponse body = new ValidationErrorResponse(
            Instant.now(),
            HttpStatus.BAD_REQUEST.value(),
            "Validation failed",
            extractPath(request),
            fieldErrors
        );
        return ResponseEntity.badRequest().body(body);
    }

    // ── 500 Catch-all ─────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleGeneric(Exception ex, WebRequest request) {
        log.error("Unhandled exception at {}: {}", extractPath(request), ex.getMessage(), ex);
        return buildResponse(
            HttpStatus.INTERNAL_SERVER_ERROR,
            "An unexpected error occurred. Please try again later.",
            request
        );
    }

    // ── Builder ───────────────────────────────────────────────

    private ResponseEntity<ErrorResponse> buildResponse(
            HttpStatus status, String message, WebRequest request) {
        ErrorResponse body = new ErrorResponse(
            Instant.now(),
            status.value(),
            status.getReasonPhrase(),
            message,
            extractPath(request)
        );
        return ResponseEntity.status(status).body(body);
    }

    private String extractPath(WebRequest request) {
        return request.getDescription(false).replace("uri=", "");
    }

    // ── Response records ──────────────────────────────────────

    public record ErrorResponse(
        Instant timestamp,
        int     status,
        String  error,
        String  message,
        String  path
    ) {}

    public record ValidationErrorResponse(
        Instant             timestamp,
        int                 status,
        String              message,
        String              path,
        Map<String, String> fieldErrors
    ) {}
}