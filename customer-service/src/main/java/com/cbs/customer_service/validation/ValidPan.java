package com.cbs.customer_service.validation;

import jakarta.validation.Constraint;
import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.Payload;

import java.lang.annotation.*;

/**
 * Validates Indian Permanent Account Number (PAN) format.
 * Format: 5 uppercase letters + 4 digits + 1 uppercase letter
 * Example: ABCDE1234F
 */
@Documented
@Constraint(validatedBy = ValidPan.PanValidator.class)
@Target({ElementType.FIELD, ElementType.PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
public @interface ValidPan {

    String message() default "Invalid PAN format. Expected format: ABCDE1234F";
    Class<?>[] groups() default {};
    Class<? extends Payload>[] payload() default {};

    class PanValidator implements ConstraintValidator<ValidPan, String> {

        private static final java.util.regex.Pattern PAN_PATTERN =
            java.util.regex.Pattern.compile("^[A-Z]{5}[0-9]{4}[A-Z]$");

        @Override
        public boolean isValid(String pan, ConstraintValidatorContext ctx) {
            // null is valid here — @NotBlank on the field handles nullability
            if (pan == null || pan.isBlank()) return true;
            return PAN_PATTERN.matcher(pan.trim().toUpperCase()).matches();
        }
    }
}