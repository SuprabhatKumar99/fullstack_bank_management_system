package com.cbs.customer_service.mapper;

import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.cbs.customer_service.dto.request.RegisterCustomerRequest;
import com.cbs.customer_service.dto.request.UpdateProfileRequest;
import com.cbs.customer_service.dto.response.CustomerResponse;
import com.cbs.customer_service.dto.response.CustomerSummaryResponse;
import com.cbs.customer_service.entity.Customer;

/**
 * MapStruct mapper for Customer entity ↔ DTOs.
 *
 * componentModel = "spring" → generates a Spring @Component bean,
 * injected normally with @Autowired / constructor injection.
 *
 * nullValuePropertyMappingStrategy = IGNORE → for PATCH updates,
 * null fields in the request are skipped (not overwritten in the entity).
 */
@Mapper(
    componentModel        = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy  = ReportingPolicy.IGNORE
)
public interface CustomerMapper {

    // ── Register → Entity ─────────────────────────────────────────

    @Mapping(target = "customerId",   ignore = true)
    @Mapping(target = "kycStatus",    ignore = true)  // defaults to PENDING in entity
    @Mapping(target = "createdAt",    ignore = true)
    @Mapping(target = "updatedAt",    ignore = true)
    @Mapping(target = "kycVerifiedAt",ignore = true)
    @Mapping(target = "kycExpiresAt", ignore = true)
    @Mapping(target = "isPep",        ignore = true)
    @Mapping(target = "isDeceased",   ignore = true)
    @Mapping(target = "riskCategory", ignore = true)
    @Mapping(target = "createdBy",    ignore = true)
    @Mapping(target = "aadhaarMasked",ignore = true)
    Customer toEntity(RegisterCustomerRequest request);

    // ── Entity → Full Response ────────────────────────────────────

    @Mapping(target = "displayName",    expression = "java(customer.getDisplayName())")
    @Mapping(target = "aadhaarLastFour",expression = "java(maskAadhaar(customer.getAadhaarMasked()))")
    CustomerResponse toResponse(Customer customer);

    // ── Entity → Summary ─────────────────────────────────────────

    @Mapping(target = "displayName", expression = "java(customer.getDisplayName())")
    CustomerSummaryResponse toSummary(Customer customer);

    // ── PATCH update (null fields are ignored) ────────────────────

    @Mapping(target = "customerId",    ignore = true)
    @Mapping(target = "customerType",  ignore = true)  // immutable after registration
    @Mapping(target = "phone",         ignore = true)  // immutable — change via OTP flow
    @Mapping(target = "panNumber",     ignore = true)  // immutable — KYC document
    @Mapping(target = "kycStatus",     ignore = true)
    @Mapping(target = "kycVerifiedAt", ignore = true)
    @Mapping(target = "kycExpiresAt",  ignore = true)
    @Mapping(target = "createdAt",     ignore = true)
    @Mapping(target = "updatedAt",     ignore = true)
    @Mapping(target = "isPep",         ignore = true)
    @Mapping(target = "isDeceased",    ignore = true)
    @Mapping(target = "riskCategory",  ignore = true)
    @Mapping(target = "createdBy",     ignore = true)
    @Mapping(target = "aadhaarMasked", ignore = true)
    @Mapping(target = "dateOfBirth",   ignore = true)
    @Mapping(target = "gender",        ignore = true)
    @Mapping(target = "homeBranchId",  ignore = true)
    @Mapping(target = "alternatePhone",ignore = true)
    void updateEntityFromRequest(UpdateProfileRequest request, @MappingTarget Customer customer);

    // ── Helper: mask Aadhaar ──────────────────────────────────────

    default String maskAadhaar(String raw) {
        if (raw == null || raw.length() < 4) return null;
        return "XXXX-XXXX-" + raw.substring(raw.length() - 4);
    }
}