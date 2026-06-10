package com.cbs.customer_service.entity;

import java.time.LocalDate;
import java.time.OffsetDateTime;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.cbs.customer_service.enums.CustomerType;
import com.cbs.customer_service.enums.Gender;
import com.cbs.customer_service.enums.KycStatus;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.Transient;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

/**
 * JPA entity for the `customers` table.
 *
 * Design notes:
 * - ddl-auto=validate → Flyway owns the schema; Hibernate only reads it.
 * - Enums stored as strings (EnumType.STRING) to survive DB enum renaming.
 * - No @Version (optimistic lock) here — customer profile updates are rare
 *   and go through the service layer which handles concurrency via SELECT FOR UPDATE.
 * - Aadhaar: store only the last 4 digits or encrypted ciphertext.
 *   The field is intentionally named `aadhaarMasked` to make this obvious.
 */
@Entity
@Table(
    name = "customers",
    indexes = {
        @Index(name = "idx_customers_pan",       columnList = "pan_number"),
        @Index(name = "idx_customers_kyc_status", columnList = "kyc_status")
    }
)
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "customer_id", updatable = false, nullable = false)
    private UUID customerId;

    @Enumerated(EnumType.STRING)
    @Column(name = "customer_type", nullable = false, length = 20)
    private CustomerType customerType;

    // ── Individual fields ────────────────────────────────────────
    @Column(name = "first_name", length = 80)
    private String firstName;

    @Column(name = "last_name", length = 80)
    private String lastName;

    @Column(name = "date_of_birth")
    private LocalDate dateOfBirth;

    @Enumerated(EnumType.STRING)
    @Column(name = "gender", length = 20)
    private Gender gender;

    // ── Business fields ──────────────────────────────────────────
    @Column(name = "business_name", length = 200)
    private String businessName;

    @Column(name = "business_type", length = 60)
    private String businessType;

    // ── Identity / KYC ──────────────────────────────────────────
    @Column(name = "pan_number", length = 10, unique = true)
    private String panNumber;

    /**
     * Stores only the last 4 digits of Aadhaar, or an application-layer
     * ciphertext. Never persist the full 12-digit number in plaintext.
     */
    @Column(name = "aadhaar_number", length = 12)
    private String aadhaarMasked;

    // ── Contact ──────────────────────────────────────────────────
    @Column(name = "phone", nullable = false, length = 15, unique = true)
    private String phone;

    @Column(name = "email", length = 254, unique = true)
    private String email;

    @Column(name = "alternate_phone", length = 15)
    private String alternatePhone;

    // ── Address ──────────────────────────────────────────────────
    @Column(name = "address_line1", length = 200)
    private String addressLine1;

    @Column(name = "address_line2", length = 200)
    private String addressLine2;

    @Column(name = "city", length = 80)
    private String city;

    @Column(name = "state", length = 80)
    private String state;

    @Column(name = "pincode", length = 6)
    private String pincode;

    @Column(name = "country", length = 2, nullable = false)
    @Builder.Default
    private String country = "IN";

    // ── KYC ──────────────────────────────────────────────────────
    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    @Builder.Default
    private KycStatus kycStatus = KycStatus.PENDING;

    @Column(name = "kyc_verified_at")
    private OffsetDateTime kycVerifiedAt;

    @Column(name = "kyc_expires_at")
    private OffsetDateTime kycExpiresAt;

    // ── Risk & Compliance ────────────────────────────────────────
    @Column(name = "risk_category", nullable = false, length = 20)
    @Builder.Default
    private String riskCategory = "LOW";

    @Column(name = "is_pep", nullable = false)
    @Builder.Default
    private Boolean isPep = false;

    @Column(name = "is_deceased", nullable = false)
    @Builder.Default
    private Boolean isDeceased = false;

    // ── Branch ───────────────────────────────────────────────────
    @Column(name = "home_branch_id")
    private UUID homeBranchId;

    @Column(name = "created_by")
    private UUID createdBy;

    // ── Audit ────────────────────────────────────────────────────
    @CreationTimestamp
    @Column(name = "created_at", updatable = false, nullable = false)
    private OffsetDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private OffsetDateTime updatedAt;

    // ── Domain helpers ───────────────────────────────────────────

    /** Full name for INDIVIDUAL customers; business name for BUSINESS. */
    @Transient
    public String getDisplayName() {
        if (customerType == CustomerType.BUSINESS) {
            return businessName;
        }
        return (firstName != null ? firstName : "") + " "
             + (lastName  != null ? lastName  : "");
    }

    /** Advance KYC to UNDER_REVIEW when docs are submitted. */
    public void submitForKycReview() {
        if (this.kycStatus != KycStatus.PENDING && this.kycStatus != KycStatus.EXPIRED) {
            throw new IllegalStateException(
                "KYC can only be submitted from PENDING or EXPIRED state, current: " + kycStatus);
        }
        this.kycStatus = KycStatus.UNDER_REVIEW;
    }

    /** Called by the KYC officer / automated verifier to approve. */
    public void approveKyc(OffsetDateTime expiresAt) {
        if (this.kycStatus != KycStatus.UNDER_REVIEW) {
            throw new IllegalStateException(
                "KYC can only be approved from UNDER_REVIEW state, current: " + kycStatus);
        }
        this.kycStatus     = KycStatus.VERIFIED;
        this.kycVerifiedAt = OffsetDateTime.now();
        this.kycExpiresAt  = expiresAt;
    }

    /** Called when KYC verification fails. */
    public void rejectKyc() {
        this.kycStatus = KycStatus.REJECTED;
    }

    /** Called by the nightly KYC expiry job. */
    public void expireKyc() {
        this.kycStatus = KycStatus.EXPIRED;
    }
}