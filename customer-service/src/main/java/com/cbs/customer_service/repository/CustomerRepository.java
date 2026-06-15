package com.cbs.customer_service.repository;


import java.time.LocalDate;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cbs.customer_service.entity.Customer;
import com.cbs.customer_service.enums.KycStatus;

@Repository
public interface CustomerRepository extends JpaRepository<Customer, UUID> {

    Optional<Customer> findByEmail(String email);

    Optional<Customer> findByPhoneNumber(String phoneNumber);

    Optional<Customer> findByCustomerNumber(String customerNumber);

    Optional<Customer> findByNationalId(String nationalId);

    boolean existsByEmail(String email);

    boolean existsByPhoneNumber(String phoneNumber);

    boolean existsByNationalId(String nationalId);

    List<Customer> findAllByKycStatus(KycStatus kycStatus);

    Page<Customer> findAllByActiveTrue(Pageable pageable);

    Page<Customer> findAllByKycStatusAndActiveTrue(KycStatus kycStatus, Pageable pageable);

    @Query("""
            SELECT c FROM Customer c
            WHERE c.active = true
              AND (LOWER(c.firstName) LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(c.lastName)  LIKE LOWER(CONCAT('%', :query, '%'))
                OR LOWER(c.email)     LIKE LOWER(CONCAT('%', :query, '%'))
                OR c.customerNumber   LIKE CONCAT('%', :query, '%'))
            """)
    Page<Customer> searchCustomers(@Param("query") String query, Pageable pageable);

    @Query("""
            SELECT COUNT(c) FROM Customer c
            WHERE c.kycStatus = :status AND c.active = true
            """)
    long countByKycStatus(@Param("status") KycStatus status);

    @Query("""
            SELECT c FROM Customer c
            WHERE c.active = true
              AND c.dateOfBirth = :dob
            """)
    List<Customer> findByDateOfBirth(@Param("dob") LocalDate dob);

    @Modifying
    @Query("UPDATE Customer c SET c.active = false WHERE c.id = :id")
    int softDeleteById(@Param("id") UUID id);
}