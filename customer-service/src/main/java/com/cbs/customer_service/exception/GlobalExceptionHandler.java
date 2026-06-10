package com.cbs.customer_service.exception;

import com.cbs.customer_service.dto.response.ApiResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.Map;
import java.util.stream.Collectors;

@RestControllerAdvice
@Slf4j
public class GlobalExceptionHandler {

    // ── Domain exceptions ─────────────────────────────────────────

    @ExceptionHandler(CustomerNotFoundException.class)
    public ResponseEntity<ApiResponse<Void>> handleNotFound(CustomerNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiResponse.fail("CUSTOMER_NOT_FOUND", ex.getMessage()));
    }

    @ExceptionHandler(DuplicateCustomerException.class)
    public ResponseEntity<ApiResponse<Void>> handleDuplicate(DuplicateCustomerException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("DUPLICATE_CUSTOMER", ex.getMessage()));
    }

    @ExceptionHandler(InvalidKycTransitionException.class)
    public ResponseEntity<ApiResponse<Void>> handleKycTransition(InvalidKycTransitionException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY)
                .body(ApiResponse.fail("INVALID_KYC_TRANSITION", ex.getMessage()));
    }

    @ExceptionHandler(IllegalStateException.class)
    public ResponseEntity<ApiResponse<Void>> handleIllegalState(IllegalStateException ex) {
        return ResponseEntity.status(HttpStatus.CONFLICT)
                .body(ApiResponse.fail("ILLEGAL_STATE", ex.getMessage()));
    }

    // ── Validation ────────────────────────────────────────────────

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ApiResponse<Void>> handleValidation(MethodArgumentNotValidException ex) {
        Map<String, String> fieldErrors = ex.getBindingResult()
                .getFieldErrors()
                .stream()
                .collect(Collectors.toMap(
                        FieldError::getField,
                        fe -> fe.getDefaultMessage() != null ? fe.getDefaultMessage() : "Invalid value",
                        (first, second) -> first   // keep first error per field
                ));

        ApiResponse<Void> body = ApiResponse.<Void>builder()
                .success(false)
                .error(ApiResponse.ErrorDetail.builder()
                        .code("VALIDATION_FAILED")
                        .message("Request validation failed")
                        .details(fieldErrors)
                        .build())
                .build();

        return ResponseEntity.badRequest().body(body);
    }

    // ── Catch-all ─────────────────────────────────────────────────

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiResponse<Void>> handleGeneric(Exception ex) {
        log.error("Unhandled exception in CustomerService", ex);
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR)
                .body(ApiResponse.fail("INTERNAL_ERROR",
                        "An unexpected error occurred. Please try again later."));
    }
}