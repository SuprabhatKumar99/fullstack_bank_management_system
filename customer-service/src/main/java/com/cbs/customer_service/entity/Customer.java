package com.cbs.customer_service.entity;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.Set;
import java.util.UUID;

import org.hibernate.annotations.CreationTimestamp;
import org.hibernate.annotations.UpdateTimestamp;

import com.cbs.customer_service.enums.KycStatus;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.Table;
import jakarta.persistence.Version;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

@Entity
@Table(name = "customers", schema = "public")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString(exclude = {"addresses"})
public class Customer {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    @Column(name = "id", updatable = false, nullable = false)
    private UUID id;

    @Column(name = "customer_number", unique = true, nullable = false, length = 20)
    private String customerNumber;

    @Column(name = "first_name", nullable = false, length = 100)
    private String firstName;

    @Column(name = "last_name", nullable = false, length = 100)
    private String lastName;

    @Column(name = "email", unique = true, nullable = false, length = 255)
    private String email;

    @Column(name = "phone_number", unique = true, nullable = false, length = 20)
    private String phoneNumber;

    @Column(name = "date_of_birth", nullable = false)
    private LocalDate dateOfBirth;

    @Column(name = "national_id", unique = true, length = 50)
    private String nationalId;

    @Column(name = "nationality", length = 100)
    private String nationality;

    @Enumerated(EnumType.STRING)
    @Column(name = "kyc_status", nullable = false, length = 20)
    private KycStatus kycStatus;

    @Column(name = "kyc_verified_at")
    private LocalDateTime kycVerifiedAt;

    @Column(name = "kyc_rejection_reason", length = 500)
    private String kycRejectionReason;

    @Column(name = "is_active", nullable = false)
    private boolean active;

    @Column(name = "risk_category", length = 20)
    private String riskCategory;

    @CreationTimestamp
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @UpdateTimestamp
    @Column(name = "updated_at", nullable = false)
    private LocalDateTime updatedAt;

    @Version
    @Column(name = "version")
    private Long version;

    @OneToMany(mappedBy = "customer", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    private Set<CustomerAddress> addresses;

    // -----------------------------------------------------------------------
    // KYC State Machine
    // Maps current status -> set of allowed next statuses
    // -----------------------------------------------------------------------
    private static final Map<KycStatus, Set<KycStatus>> KYC_TRANSITIONS = Map.of(
            KycStatus.PENDING,     Set.of(KycStatus.UNDER_REVIEW, KycStatus.REJECTED),
            KycStatus.UNDER_REVIEW, Set.of(KycStatus.APPROVED, KycStatus.REJECTED, KycStatus.ADDITIONAL_INFO_REQUIRED),
            KycStatus.ADDITIONAL_INFO_REQUIRED, Set.of(KycStatus.UNDER_REVIEW, KycStatus.REJECTED),
            KycStatus.APPROVED,    Set.of(KycStatus.SUSPENDED),
            KycStatus.SUSPENDED,   Set.of(KycStatus.APPROVED, KycStatus.REJECTED),
            KycStatus.REJECTED,    Set.of()
    );

    /**
     * Transition KYC status following the defined state machine.
     *
     * @throws IllegalStateException if the transition is not permitted
     */
    public void transitionKyc(KycStatus newStatus, String rejectionReason) {
        Set<KycStatus> allowed = KYC_TRANSITIONS.getOrDefault(this.kycStatus, Set.of());
        if (!allowed.contains(newStatus)) {
            throw new IllegalStateException(
                    "KYC transition from [" + this.kycStatus + "] to [" + newStatus + "] is not permitted."
            );
        }
        this.kycStatus = newStatus;
        if (newStatus == KycStatus.APPROVED) {
            this.kycVerifiedAt = LocalDateTime.now();
            this.kycRejectionReason = null;
        }
        if (newStatus == KycStatus.REJECTED || newStatus == KycStatus.ADDITIONAL_INFO_REQUIRED) {
            this.kycRejectionReason = rejectionReason;
        }
    }

    /**
     * Check if this customer is fully KYC-verified and active.
     */
    public boolean isFullyVerified() {
        return this.active && this.kycStatus == KycStatus.APPROVED;
    }

    /**
     * Deactivate customer (soft delete).
     */
    public void deactivate() {
        this.active = false;
    }

    /**
     * Get customer's full name.
     */
    public String getFullName() {
        return this.firstName + " " + this.lastName;
    }
}