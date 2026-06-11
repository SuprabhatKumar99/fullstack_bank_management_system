package com.cbs.account_service.mapper;


import org.mapstruct.Mapper;
import org.mapstruct.Mapping;
import org.mapstruct.MappingTarget;
import org.mapstruct.NullValuePropertyMappingStrategy;
import org.mapstruct.ReportingPolicy;

import com.cbs.account_service.dto.request.OpenAccountRequest;
import com.cbs.account_service.dto.request.UpdateAccountRequest;
import com.cbs.account_service.dto.response.AccountResponse;
import com.cbs.account_service.dto.response.AccountSummaryResponse;
import com.cbs.account_service.dto.response.BalanceResponse;
import com.cbs.account_service.entity.Account;

@Mapper(
    componentModel       = "spring",
    nullValuePropertyMappingStrategy = NullValuePropertyMappingStrategy.IGNORE,
    unmappedTargetPolicy = ReportingPolicy.IGNORE
)
public interface AccountMapper {

    // ── OpenAccountRequest → Entity ───────────────────────────────
    @Mapping(target = "accountId",     ignore = true)
    @Mapping(target = "accountNumber", ignore = true)  // set by DB sequence
    @Mapping(target = "balance",       ignore = true)  // starts at zero
    @Mapping(target = "holdAmount",    ignore = true)
    @Mapping(target = "status",        ignore = true)  // defaults to PENDING_ACTIVATION
    @Mapping(target = "activatedAt",   ignore = true)
    @Mapping(target = "createdAt",     ignore = true)
    @Mapping(target = "updatedAt",     ignore = true)
    @Mapping(target = "createdBy",     ignore = true)
    @Mapping(target = "version",       ignore = true)
    @Mapping(target = "minimumBalance",ignore = true)  // set by service per account type
    @Mapping(target = "interestRate",  ignore = true)  // set by service per account type
    @Mapping(target = "maturityDate",  ignore = true)  // computed from tenureMonths
    @Mapping(target = "maturityAmount",ignore = true)  // computed at maturity
    @Mapping(target = "isFrozen",      ignore = true)
    @Mapping(target = "dormantSince",  ignore = true)
    @Mapping(target = "closedAt",      ignore = true)
    @Mapping(target = "closureReason", ignore = true)
    @Mapping(target = "lastInterestCalcDate", ignore = true)
    Account toEntity(OpenAccountRequest request);

    // ── Entity → Full Response ────────────────────────────────────
    @Mapping(target = "availableBalance",
             expression = "java(account.getAvailableBalance())")
    AccountResponse toResponse(Account account);

    // ── Entity → Summary ─────────────────────────────────────────
    @Mapping(target = "availableBalance",
             expression = "java(account.getAvailableBalance())")
    AccountSummaryResponse toSummary(Account account);

    // ── Entity → Balance Response ─────────────────────────────────
    @Mapping(target = "availableBalance",
             expression = "java(account.getAvailableBalance())")
    @Mapping(target = "cachedAt",
             expression = "java(java.time.OffsetDateTime.now())")
    @Mapping(target = "status",
             expression = "java(account.getStatus().name())")
    BalanceResponse toBalanceResponse(Account account);

    // ── PATCH update ──────────────────────────────────────────────
    @Mapping(target = "accountId",     ignore = true)
    @Mapping(target = "accountNumber", ignore = true)
    @Mapping(target = "customerId",    ignore = true)
    @Mapping(target = "branchId",      ignore = true)
    @Mapping(target = "accountType",   ignore = true)
    @Mapping(target = "currency",      ignore = true)
    @Mapping(target = "balance",       ignore = true)
    @Mapping(target = "holdAmount",    ignore = true)
    @Mapping(target = "minimumBalance",ignore = true)
    @Mapping(target = "interestRate",  ignore = true)
    @Mapping(target = "status",        ignore = true)
    @Mapping(target = "isFrozen",      ignore = true)
    @Mapping(target = "createdAt",     ignore = true)
    @Mapping(target = "updatedAt",     ignore = true)
    @Mapping(target = "createdBy",     ignore = true)
    @Mapping(target = "version",       ignore = true)
    @Mapping(target = "maturityDate",  ignore = true)
    @Mapping(target = "maturityAmount",ignore = true)
    @Mapping(target = "parentAccountId", ignore = true)
    @Mapping(target = "activatedAt",   ignore = true)
    @Mapping(target = "dormantSince",  ignore = true)
    @Mapping(target = "closedAt",      ignore = true)
    @Mapping(target = "closureReason", ignore = true)
    @Mapping(target = "isJointAccount",ignore = true)
    @Mapping(target = "isNriAccount",  ignore = true)
    @Mapping(target = "lastInterestCalcDate", ignore = true)
    void updateFromRequest(UpdateAccountRequest request, @MappingTarget Account account);
}