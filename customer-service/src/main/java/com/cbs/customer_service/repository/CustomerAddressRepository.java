package com.cbs.customer_service.repository;


import java.util.List;
import java.util.Optional;
import java.util.UUID;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import com.cbs.customer_service.entity.CustomerAddress;
import com.cbs.customer_service.enums.AddressType;

@Repository
public interface CustomerAddressRepository extends JpaRepository<CustomerAddress, UUID> {

    List<CustomerAddress> findAllByCustomerId(UUID customerId);

    Optional<CustomerAddress> findByCustomerIdAndAddressType(UUID customerId, AddressType addressType);

    Optional<CustomerAddress> findByCustomerIdAndPrimaryTrue(UUID customerId);

    boolean existsByCustomerIdAndAddressType(UUID customerId, AddressType addressType);

    @Modifying
    @Query("UPDATE CustomerAddress a SET a.primary = false WHERE a.customer.id = :customerId")
    int clearPrimaryForCustomer(@Param("customerId") UUID customerId);

    void deleteAllByCustomerId(UUID customerId);
}