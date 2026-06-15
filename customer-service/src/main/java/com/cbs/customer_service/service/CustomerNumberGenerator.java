package com.cbs.customer_service.service;

import org.springframework.stereotype.Component;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Generates unique, human-readable customer numbers in the format:
 *   CBS-YYYYMM-XXXXXXXX
 *
 * Example: CBS-202406-00000001
 *
 * The counter resets at application startup. For production, a database
 * sequence (e.g. nextval('customer_number_seq')) should be used instead.
 */
@Component
public class CustomerNumberGenerator {

    private static final String PREFIX = "CBS";
    private static final DateTimeFormatter MONTH_FORMATTER = DateTimeFormatter.ofPattern("yyyyMM");
    private final AtomicLong counter = new AtomicLong(0);

    public String generate() {
        String yearMonth = LocalDate.now().format(MONTH_FORMATTER);
        long sequence = counter.incrementAndGet();
        return String.format("%s-%s-%08d", PREFIX, yearMonth, sequence);
    }
}