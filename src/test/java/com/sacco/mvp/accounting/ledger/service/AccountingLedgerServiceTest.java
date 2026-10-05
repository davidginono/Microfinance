package com.sacco.mvp.accounting.ledger.service;

import com.sacco.mvp.accounting.ledger.dto.LedgerDtos.*;
import com.sacco.mvp.accounting.ledger.exception.LedgerException;
import com.sacco.mvp.accounting.ledger.repository.AccountingLedgerRepository;
import com.sacco.mvp.accounting.policy.service.AccountingPolicyService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.service.ApplicationClock;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class AccountingLedgerServiceTest {
    private final AccountingLedgerRepository repository=mock(AccountingLedgerRepository.class);
    private final AccountingLedgerService service=new AccountingLedgerService(repository,mock(AccountingPolicyService.class),new AccessControlService(),new ApplicationClock("Africa/Nairobi"));
    @Test void decimalImportAndJsonRetainHighAmountsAndCents() {
        var lines=service.parseLines("1000,9999999999999999.99,0,verified source\n3000,0,9999999999999999.99");
        assertThat(lines.getFirst().debit()).isEqualByComparingTo("9999999999999999.99");
        assertThat(service.parseLines("1000,10.01,0\r\n3000,0,10.01")).hasSize(2);
        var mapper=JsonMapper.builder().build();
        LineCommand decoded=mapper.readValue("{\"accountCode\":\"1000\",\"debit\":9999999999999999.99,\"credit\":0,\"memo\":\"test\"}",LineCommand.class);
        assertThat(decoded.debit()).isEqualByComparingTo("9999999999999999.99");
    }
    @Test void importsRejectIncompleteMalformedOrExcessRows() {
        assertThatThrownBy(()->service.parseLines("1000,1,0")).isInstanceOf(LedgerException.class);
        assertThatThrownBy(()->service.parseLines("1000,not-money,0\n3000,0,1")).isInstanceOf(LedgerException.class);
        assertThatThrownBy(()->service.parseLines("1000,1,0\n".repeat(201))).isInstanceOf(LedgerException.class);
    }
    @Test void claimDoesNotAuthorizePlatformIdentityOrClientSessions() {
        AppUserPrincipal platform=principal(Position.ADMIN,true,Set.of(UserClaim.ACCOUNTING_VIEW));
        assertThatThrownBy(()->service.accounts(platform,0)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.accounts(principal(Position.MEMBER,false,Set.of(UserClaim.ACCOUNTING_VIEW)),0)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.accounts(principal(Position.ACCOUNTANT,true,Set.of()),0)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }
    @Test void classifiedLoanBalanceCannotBecomeManualPostingAccount() {
        AppUserPrincipal accountant=principal(Position.ACCOUNTANT,true,Set.of(UserClaim.ACCOUNTING_ACCOUNTS_MANAGE));
        assertThatThrownBy(()->service.createAccount(accountant,new AccountCommand("LOAN","Principal",AccountKind.ASSET,NormalBalance.DEBIT,null,AccountUsage.POSTING,AccountCategory.LOAN_PRINCIPAL))).hasMessageContaining("control");
        assertThatThrownBy(()->service.createAccount(accountant,new AccountCommand("LOAN","Principal",AccountKind.INCOME,NormalBalance.CREDIT,null,AccountUsage.CONTROL,AccountCategory.LOAN_PRINCIPAL))).hasMessageContaining("classification");
        verifyNoInteractions(repository);
    }
    @Test void prepaymentsAndExpenseAccrualsKeepDistinctAssetAndLiabilityClassification() {
        AppUserPrincipal actor=principal(Position.ACCOUNTANT,true,Set.of(UserClaim.ACCOUNTING_ACCOUNTS_MANAGE));
        assertThatThrownBy(()->service.createAccount(actor,new AccountCommand("PRE","Prepayment",AccountKind.EXPENSE,NormalBalance.DEBIT,null,AccountUsage.POSTING,AccountCategory.PREPAYMENT))).hasMessageContaining("classification");
        assertThatThrownBy(()->service.createAccount(actor,new AccountCommand("ACC","Accrued expense",AccountKind.ASSET,NormalBalance.DEBIT,null,AccountUsage.POSTING,AccountCategory.EXPENSE_ACCRUAL))).hasMessageContaining("classification");
        assertThat(service.createAccount(actor,new AccountCommand("PRE","Prepayment",AccountKind.ASSET,NormalBalance.DEBIT,null,AccountUsage.POSTING,AccountCategory.PREPAYMENT))).isNotNull();
        assertThat(service.createAccount(actor,new AccountCommand("ACC","Accrued expense",AccountKind.LIABILITY,NormalBalance.CREDIT,null,AccountUsage.POSTING,AccountCategory.EXPENSE_ACCRUAL))).isNotNull();
        verify(repository,times(2)).createAccount(any(),eq("TEST"),eq(actor.getMemberId()),any(),any());
    }
    @Test void sourceMetadataRequiresScopedPostingClaimAndAnActivePostingAccount() {
        AppUserPrincipal actor=principal(Position.ACCOUNTANT,true,Set.of(UserClaim.ACCOUNTING_JOURNAL_POST));
        AccountView cash=new AccountView(UUID.randomUUID(),"1000","Cash", "ASSET","DEBIT",null,"POSTING","CASH",true);
        when(repository.account("TEST","1000")).thenReturn(Optional.of(cash));
        assertThat(service.sourceAccount(actor,"1000")).isEqualTo(cash);
        assertThatThrownBy(()->service.sourceAccount(principal(Position.ACCOUNTANT,true,Set.of(UserClaim.ACCOUNTING_VIEW)),"1000")).isInstanceOf(AccessDeniedException.class);
        when(repository.account("TEST","1000")).thenReturn(Optional.of(new AccountView(cash.id(),"1000","Cash","ASSET","DEBIT",null,"POSTING","CASH",false)));
        assertThatThrownBy(()->service.sourceAccount(actor,"1000")).hasMessageContaining("account");
    }
    private AppUserPrincipal principal(Position position,boolean staff,Set<UserClaim> claims) {
        return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("TEST").stationId("BRANCH").memberNo("TEST-USER").position(position).status(MemberStatus.ACTIVE).memberAccount(true).build(),claims,staff);
    }
}
