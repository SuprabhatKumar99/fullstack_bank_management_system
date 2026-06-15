package com.cbs.customer_service.mapper;

import java.util.List;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.cbs.customer_service.dto.request.AddressRequest;
import com.cbs.customer_service.dto.request.CustomerProfileUpdateRequest;
import com.cbs.customer_service.dto.request.CustomerRegistrationRequest;
import com.cbs.customer_service.dto.response.AddressResponse;
import com.cbs.customer_service.dto.response.CustomerResponse;
import com.cbs.customer_service.entity.Customer;
import com.cbs.customer_service.entity.CustomerAddress;

@Mapper(
        componentModel = "spring",
        nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
        unmappedTargetPolicy = ReportingPolicy.WARN
)
public interface CustomerMapper {

    @Mapping(target = "id",                ignore = true)
    @Mapping(target = "customerNumber",    ignore = true)
    @Mapping(target = "kycStatus",         ignore = true)
    @Mapping(target = "kycVerifiedAt",     ignore = true)
    @Mapping(target = "kycRejectionReason",ignore = true)
    @Mapping(target = "active",            ignore = true)
    @Mapping(target = "riskCategory",      ignore = true)
    @Mapping(target = "createdAt",         ignore = true)
    @Mapping(target = "updatedAt",         ignore = true)
    @Mapping(target = "version",           ignore = true)
    @Mapping(target = "addresses",         ignore = true)
    Customer toEntity(CustomerRegistrationRequest request);

    @Mapping(target = "fullName", expression = "java(customer.getFullName())")
    CustomerResponse toResponse(Customer customer);

    List<CustomerResponse> toResponseList(List<Customer> customers);

    @Mapping(target = "id",         ignore = true)
    @Mapping(target = "customer",   ignore = true)
    @Mapping(target = "createdAt",  ignore = true)
    @Mapping(target = "updatedAt",  ignore = true)
    CustomerAddress toAddressEntity(AddressRequest request);

    AddressResponse toAddressResponse(CustomerAddress address);

    List<AddressResponse> toAddressResponseList(List<CustomerAddress> addresses);

    /**
     * Partial update — only non-null fields from the source are applied.
     * NullValuePropertyMappingStrategy.IGNORE handles this automatically.
     */
    @Mapping(target = "id",                ignore = true)
    @Mapping(target = "customerNumber",    ignore = true)
    @Mapping(target = "dateOfBirth",       ignore = true)
    @Mapping(target = "nationalId",        ignore = true)
    @Mapping(target = "kycStatus",         ignore = true)
    @Mapping(target = "kycVerifiedAt",     ignore = true)
    @Mapping(target = "kycRejectionReason",ignore = true)
    @Mapping(target = "active",            ignore = true)
    @Mapping(target = "riskCategory",      ignore = true)
    @Mapping(target = "createdAt",         ignore = true)
    @Mapping(target = "updatedAt",         ignore = true)
    @Mapping(target = "version",           ignore = true)
    @Mapping(target = "addresses",         ignore = true)
    void updateCustomerFromRequest(
            CustomerProfileUpdateRequest request,
            @MappingTarget Customer customer
    );
}