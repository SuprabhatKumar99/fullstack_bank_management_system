package com.cbs.account_service.service;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.BDDMockito.given;
import static org.mockito.BDDMockito.then;

import java.math.BigDecimal;
import java.util.Optional;
import java.util.UUID;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.test.util.ReflectionTestUtils;

import com.cbs.account_service.dto.request.CloseAccountRequest;
import com.cbs.account_service.dto.request.OpenAccountRequest;
import com.cbs.account_service.dto.response.AccountResponse;
import com.cbs.account_service.dto.response.BalanceResponse;
import com.cbs.account_service.entity.Account;
import com.cbs.account_service.enums.AccountStatus;
import com.cbs.account_service.enums.AccountType;
import com.cbs.account_service.exception.AccountOperationException;
import com.cbs.account_service.exception.InsufficientFundsException;
import com.cbs.account_service.mapper.AccountMapper;
import com.cbs.account_service.repository.AccountRepository;

@ExtendWith(MockitoExtension.class)
@DisplayName("AccountService")
class AccountServiceTest {

    @Mock AccountRepository accountRepository;
    @Mock AccountMapper      accountMapper;
    @Mock KafkaTemplate<String, Object> kafkaTemplate;

    @InjectMocks AccountService accountService;

    private final UUID accountId  = UUID.randomUUID();
    private final UUID customerId = UUID.randomUUID();
    private final UUID branchId   = UUID.randomUUID();
    private final UUID staffId    = UUID.randomUUID();

    @BeforeEach
    void injectValues() {
        // Inject @Value fields that Spring can't set in unit tests
        ReflectionTestUtils.setField(accountService, "savingsMinBalance",  new BigDecimal("1000.00"));
        ReflectionTestUtils.setField(accountService, "currentMinBalance",  new BigDecimal("5000.00"));
        ReflectionTestUtils.setField(accountService, "fdMinDeposit",       new BigDecimal("10000.00"));
        ReflectionTestUtils.setField(accountService, "rdMinInstalment",    new BigDecimal("500.00"));
        ReflectionTestUtils.setField(accountService, "topicAccountOpened", "account.opened");
        ReflectionTestUtils.setField(accountService, "topicAccountClosed", "account.closed");
        ReflectionTestUtils.setField(accountService, "topicAccountFrozen", "account.frozen");
    }

    // ── Fixtures ──────────────────────────────────────────────────

    private Account buildActiveAccount(AccountType type) {
        return Account.builder()
            .accountId(accountId)
            .accountNumber("CBS00000001001")
            .customerId(customerId)
            .branchId(branchId)
            .accountType(type)
            .currency("INR")
            .balance(new BigDecimal("10000.00"))
            .holdAmount(BigDecimal.ZERO)
            .overdraftLimit(BigDecimal.ZERO)
            .minimumBalance(new BigDecimal("1000.00"))
            .status(AccountStatus.ACTIVE)
            .isFrozen(false)
            .build();
    }

    private AccountResponse buildAccountResponse(Account account) {
        return AccountResponse.builder()
            .accountId(account.getAccountId())
            .accountNumber(account.getAccountNumber())
            .customerId(account.getCustomerId())
            .accountType(account.getAccountType())
            .balance(account.getBalance())
            .availableBalance(account.getAvailableBalance())
            .status(account.getStatus())
            .build();
    }

    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("openAccount()")
    class OpenAccountTests {

        @Test
        @DisplayName("should open SAVINGS account with correct defaults")
        void openSavings_appliesDefaults() {
            OpenAccountRequest req = new OpenAccountRequest();
            req.setCustomerId(customerId);
            req.setBranchId(branchId);
            req.setAccountType(AccountType.SAVINGS);
            req.setCurrency("INR");

            Account entity = Account.builder()
                .accountId(accountId).customerId(customerId).branchId(branchId)
                .accountType(AccountType.SAVINGS).currency("INR")
                .balance(BigDecimal.ZERO).holdAmount(BigDecimal.ZERO)
                .overdraftLimit(BigDecimal.ZERO).status(AccountStatus.PENDING_ACTIVATION)
                .isFrozen(false).isJointAccount(false).isNriAccount(false).version(0L)
                .build();

            given(accountMapper.toEntity(any())).willReturn(entity);
            given(accountRepository.save(any())).willAnswer(inv -> {
                Account a = inv.getArgument(0);
                a.setAccountNumber("CBS00000001001");
                return a;
            });
            given(accountMapper.toResponse(any())).willAnswer(inv ->
                buildAccountResponse(inv.getArgument(0)));

            AccountResponse result = accountService.openAccount(req, staffId);

            assertThat(result).isNotNull();
            // verify minimum balance was set
            assertThat(entity.getMinimumBalance())
                .isEqualByComparingTo(new BigDecimal("1000.00"));
            // verify interest rate was set
            assertThat(entity.getInterestRate())
                .isEqualByComparingTo(new BigDecimal("3.50"));
            then(kafkaTemplate).should().send(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("should throw when FD initial deposit is below minimum")
        void openFD_belowMinDeposit_throws() {
            OpenAccountRequest req = new OpenAccountRequest();
            req.setCustomerId(customerId);
            req.setBranchId(branchId);
            req.setAccountType(AccountType.FIXED_DEPOSIT);
            req.setTenureMonths(12);
            req.setParentAccountId(UUID.randomUUID());
            req.setInitialDeposit(new BigDecimal("5000.00")); // below ₹10,000 minimum

            Account entity = Account.builder()
                .accountId(accountId).customerId(customerId).branchId(branchId)
                .accountType(AccountType.FIXED_DEPOSIT).currency("INR")
                .balance(BigDecimal.ZERO).holdAmount(BigDecimal.ZERO)
                .overdraftLimit(BigDecimal.ZERO).status(AccountStatus.PENDING_ACTIVATION)
                .isFrozen(false).isJointAccount(false).isNriAccount(false).version(0L)
                .build();

            given(accountMapper.toEntity(any())).willReturn(entity);
            given(accountRepository.existsById(any())).willReturn(true);

            assertThatThrownBy(() -> accountService.openAccount(req, staffId))
                .isInstanceOf(AccountOperationException.class)
                .hasMessageContaining("minimum deposit");
        }

        @Test
        @DisplayName("should throw when FD has no tenure")
        void openFD_noTenure_throws() {
            OpenAccountRequest req = new OpenAccountRequest();
            req.setCustomerId(customerId); req.setBranchId(branchId);
            req.setAccountType(AccountType.FIXED_DEPOSIT);
            req.setParentAccountId(UUID.randomUUID());
            req.setInitialDeposit(new BigDecimal("50000.00"));
            // tenureMonths intentionally null

            Account entity = Account.builder()
                .accountId(accountId).customerId(customerId).branchId(branchId)
                .accountType(AccountType.FIXED_DEPOSIT).currency("INR")
                .balance(BigDecimal.ZERO).holdAmount(BigDecimal.ZERO)
                .overdraftLimit(BigDecimal.ZERO).status(AccountStatus.PENDING_ACTIVATION)
                .isFrozen(false).isJointAccount(false).isNriAccount(false).version(0L)
                .build();

            given(accountMapper.toEntity(any())).willReturn(entity);
            given(accountRepository.existsById(any())).willReturn(true);

            assertThatThrownBy(() -> accountService.openAccount(req, staffId))
                .isInstanceOf(AccountOperationException.class)
                .hasMessageContaining("Tenure");
        }

        @Test
        @DisplayName("should throw when FD/RD has no parentAccountId")
        void openFD_noParent_throws() {
            OpenAccountRequest req = new OpenAccountRequest();
            req.setCustomerId(customerId); req.setBranchId(branchId);
            req.setAccountType(AccountType.FIXED_DEPOSIT);
            req.setTenureMonths(12);
            req.setInitialDeposit(new BigDecimal("50000.00"));
            // parentAccountId intentionally null

            Account entity = Account.builder()
                .accountId(accountId).customerId(customerId).branchId(branchId)
                .accountType(AccountType.FIXED_DEPOSIT).currency("INR")
                .balance(BigDecimal.ZERO).holdAmount(BigDecimal.ZERO)
                .overdraftLimit(BigDecimal.ZERO).status(AccountStatus.PENDING_ACTIVATION)
                .isFrozen(false).isJointAccount(false).isNriAccount(false).version(0L)
                .build();

            assertThatThrownBy(() -> accountService.openAccount(req, staffId))
                .isInstanceOf(AccountOperationException.class)
                .hasMessageContaining("parent");
        }
    }

    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("getBalance()")
    class BalanceTests {

        @Test
        @DisplayName("should return balance response for active account")
        void getBalance_active_returns() {
            Account account = buildActiveAccount(AccountType.SAVINGS);
            BalanceResponse balResp = BalanceResponse.builder()
                .accountId(accountId).balance(new BigDecimal("10000.00"))
                .availableBalance(new BigDecimal("10000.00")).build();

            given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
            given(accountMapper.toBalanceResponse(account)).willReturn(balResp);

            BalanceResponse result = accountService.getBalance(accountId);

            assertThat(result.getBalance()).isEqualByComparingTo("10000.00");
        }

        @Test
        @DisplayName("should throw for CLOSED account balance")
        void getBalance_closed_throws() {
            Account account = buildActiveAccount(AccountType.SAVINGS);
            account.setStatus(AccountStatus.CLOSED);

            given(accountRepository.findById(accountId)).willReturn(Optional.of(account));

            assertThatThrownBy(() -> accountService.getBalance(accountId))
                .isInstanceOf(AccountOperationException.class)
                .hasMessageContaining("CLOSED");
        }
    }

    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("validateDebit()")
    class ValidateDebitTests {

        @Test
        @DisplayName("should pass when balance is sufficient")
        void validateDebit_sufficient_passes() {
            Account account = buildActiveAccount(AccountType.SAVINGS);
            given(accountRepository.findByIdForUpdate(accountId))
                .willReturn(Optional.of(account));

            // No exception expected
            assertThatCode(() ->
                accountService.validateDebit(accountId, new BigDecimal("5000.00")))
                .doesNotThrowAnyException();
        }

        @Test
        @DisplayName("should throw InsufficientFundsException when balance low")
        void validateDebit_insufficient_throws() {
            Account account = buildActiveAccount(AccountType.SAVINGS);
            given(accountRepository.findByIdForUpdate(accountId))
                .willReturn(Optional.of(account));

            assertThatThrownBy(() ->
                accountService.validateDebit(accountId, new BigDecimal("15000.00")))
                .isInstanceOf(InsufficientFundsException.class)
                .hasMessageContaining("Insufficient funds");
        }

        @Test
        @DisplayName("should throw AccountOperationException when frozen")
        void validateDebit_frozen_throws() {
            Account account = buildActiveAccount(AccountType.SAVINGS);
            account.setIsFrozen(true);

            given(accountRepository.findByIdForUpdate(accountId))
                .willReturn(Optional.of(account));

            assertThatThrownBy(() ->
                accountService.validateDebit(accountId, new BigDecimal("100.00")))
                .isInstanceOf(AccountOperationException.class)
                .hasMessageContaining("not operational");
        }
    }

    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("closeAccount()")
    class CloseAccountTests {

        @Test
        @DisplayName("should close account with zero balance")
        void close_zeroBalance_succeeds() {
            Account account = buildActiveAccount(AccountType.SAVINGS);
            account.setBalance(BigDecimal.ZERO);

            given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
            given(accountRepository.save(any())).willReturn(account);
            given(accountMapper.toResponse(any())).willReturn(buildAccountResponse(account));

            CloseAccountRequest req = new CloseAccountRequest();
            req.setReason("Customer requested closure");

            AccountResponse result = accountService.closeAccount(accountId, req);

            assertThat(account.getStatus()).isEqualTo(AccountStatus.CLOSED);
            assertThat(account.getClosureReason()).isEqualTo("Customer requested closure");
            then(kafkaTemplate).should().send(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("should throw when balance is non-zero")
        void close_nonZeroBalance_throws() {
            Account account = buildActiveAccount(AccountType.SAVINGS);
            // balance is 10000 — cannot close

            given(accountRepository.findById(accountId)).willReturn(Optional.of(account));

            CloseAccountRequest req = new CloseAccountRequest();
            req.setReason("Customer requested");

            assertThatThrownBy(() -> accountService.closeAccount(accountId, req))
                .isInstanceOf(AccountOperationException.class)
                .hasMessageContaining("non-zero balance");
        }
    }

    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("freezeAccount() / unfreezeAccount()")
    class FreezeTests {

        @Test
        @DisplayName("freeze should set isFrozen=true and publish event")
        void freeze_setsFlag_publishesEvent() {
            Account account = buildActiveAccount(AccountType.SAVINGS);
            given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
            given(accountRepository.save(any())).willReturn(account);
            given(accountMapper.toResponse(any())).willReturn(buildAccountResponse(account));

            accountService.freezeAccount(accountId, "Suspicious activity");

            assertThat(account.getIsFrozen()).isTrue();
            then(kafkaTemplate).should().send(anyString(), anyString(), any());
        }

        @Test
        @DisplayName("unfreeze should clear isFrozen flag")
        void unfreeze_clearsFlag() {
            Account account = buildActiveAccount(AccountType.SAVINGS);
            account.setIsFrozen(true);

            given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
            given(accountRepository.save(any())).willReturn(account);
            given(accountMapper.toResponse(any())).willReturn(buildAccountResponse(account));

            accountService.unfreezeAccount(accountId);

            assertThat(account.getIsFrozen()).isFalse();
        }
    }

    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("placeHold() / releaseHold()")
    class HoldTests {

        @Test
        @DisplayName("placeHold should increase holdAmount")
        void placeHold_increases() {
            Account account = buildActiveAccount(AccountType.SAVINGS);
            given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
            given(accountRepository.save(any())).willReturn(account);

            accountService.placeHold(accountId, new BigDecimal("2000.00"));

            assertThat(account.getHoldAmount()).isEqualByComparingTo("2000.00");
            // available balance = 10000 - 2000 = 8000
            assertThat(account.getAvailableBalance()).isEqualByComparingTo("8000.00");
        }

        @Test
        @DisplayName("releaseHold should decrease holdAmount")
        void releaseHold_decreases() {
            Account account = buildActiveAccount(AccountType.SAVINGS);
            account.setHoldAmount(new BigDecimal("3000.00"));

            given(accountRepository.findById(accountId)).willReturn(Optional.of(account));
            given(accountRepository.save(any())).willReturn(account);

            accountService.releaseHold(accountId, new BigDecimal("1000.00"));

            assertThat(account.getHoldAmount()).isEqualByComparingTo("2000.00");
        }

        @Test
        @DisplayName("releaseHold beyond held amount should throw")
        void releaseHold_beyondHeld_throws() {
            Account account = buildActiveAccount(AccountType.SAVINGS);
            account.setHoldAmount(new BigDecimal("500.00"));

            given(accountRepository.findById(accountId)).willReturn(Optional.of(account));

            assertThatThrownBy(() ->
                accountService.releaseHold(accountId, new BigDecimal("1000.00")))
                .isInstanceOf(AccountOperationException.class)
                .hasMessageContaining("Cannot release more than held");
        }
    }

    // ─────────────────────────────────────────────────────────────
    @Nested
    @DisplayName("Account domain invariants")
    class DomainInvariantTests {

        @Test
        @DisplayName("availableBalance correctly accounts for hold and overdraft")
        void availableBalance_calculation() {
            Account account = Account.builder()
                .accountId(accountId)
                .balance(new BigDecimal("5000.00"))
                .holdAmount(new BigDecimal("1000.00"))
                .overdraftLimit(new BigDecimal("2000.00"))
                .status(AccountStatus.ACTIVE)
                .isFrozen(false)
                .isJointAccount(false).isNriAccount(false).version(0L)
                .build();

            // available = 5000 - 1000 + 2000 = 6000
            assertThat(account.getAvailableBalance())
                .isEqualByComparingTo(new BigDecimal("6000.00"));
        }

        @Test
        @DisplayName("canDebit returns false when frozen regardless of balance")
        void canDebit_frozenAccount_returnsFalse() {
            Account account = buildActiveAccount(AccountType.SAVINGS);
            account.setIsFrozen(true);

            assertThat(account.canDebit(new BigDecimal("100.00"))).isFalse();
        }

        @Test
        @DisplayName("activate throws when account is not PENDING_ACTIVATION")
        void activate_alreadyActive_throws() {
            Account account = buildActiveAccount(AccountType.SAVINGS);
            // Already ACTIVE — cannot re-activate

            assertThatThrownBy(account::activate)
                .isInstanceOf(AccountOperationException.class)
                .hasMessageContaining("PENDING_ACTIVATION");
        }
    }
}
