package com.cbs.customer_service.repository;


import com.cbs.customer_service.entity.Customer;
import com.cbs.customer_service.enums.KycStatus;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    // ── Existence / dedup checks (used during registration) ──────

    boolean existsByPhone(String phone);

    boolean existsByEmail(String email);

    boolean existsByPanNumber(String panNumber);

    // ── Single-customer lookups ───────────────────────────────────

    Optional<Customer> findByPhone(String phone);

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByPanNumber(String panNumber);

    // ── Teller UI search ─────────────────────────────────────────
    // Case-insensitive last-name search with phone fallback.

    @Query("""
        SELECT c FROM Customer c
        WHERE (:lastName  IS NULL OR LOWER(c.lastName)  LIKE LOWER(CONCAT('%', :lastName,  '%')))
          AND (:firstName IS NULL OR LOWER(c.firstName) LIKE LOWER(CONCAT('%', :firstName, '%')))
        ORDER BY c.lastName, c.firstName
        """)
    Page<Customer> searchByName(
        @Param("firstName") String firstName,
        @Param("lastName")  String lastName,
        Pageable pageable
    );

    // ── KYC pipeline queries ──────────────────────────────────────

    Page<Customer> findByKycStatus(KycStatus kycStatus, Pageable pageable);

    /** Customers whose KYC has expired — nightly job picks these up. */
    @Query("""
        SELECT c FROM Customer c
        WHERE c.kycStatus = 'VERIFIED'
          AND c.kycExpiresAt < :now
        """)
    List<Customer> findExpiredKyc(@Param("now") OffsetDateTime now);

    /** Bulk expire — called by the nightly KYC expiry batch job. */
    @Modifying
    @Query("""
        UPDATE Customer c
        SET c.kycStatus = com.cbs.customer_service.enums.KycStatus.EXPIRED,
            c.updatedAt = :now
        WHERE c.kycStatus = com.cbs.customer_service.enums.KycStatus.VERIFIED
          AND c.kycExpiresAt < :now
        """)
    int bulkExpireKyc(@Param("now") OffsetDateTime now);

    // ── Branch-level queries ──────────────────────────────────────

    Page<Customer> findByHomeBranchId(UUID branchId, Pageable pageable);

    // ── Compliance / AML ─────────────────────────────────────────

    Page<Customer> findByIsPepTrue(Pageable pageable);

    @Query("""
        SELECT c FROM Customer c
        WHERE c.riskCategory = :category
        ORDER BY c.createdAt DESC
        """)
    Page<Customer> findByRiskCategory(@Param("category") String category, Pageable pageable);
}