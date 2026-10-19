package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.repository.LoanProductAccountRepository;
import com.sacco.mvp.accounting.loans.service.LoanProductAccountService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.Test;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class LoanProductAccountServiceTest {
    final LoanProductAccountRepository repository=mock(LoanProductAccountRepository.class);
    final AuditService audit=mock(AuditService.class);
    final LoanProductAccountService service=new LoanProductAccountService(repository,new ApplicationClock("Africa/Nairobi"),audit);
    final UUID principal=UUID.randomUUID(),interest=UUID.randomUUID(),productId=UUID.randomUUID();
    final LoanProductSetting product=LoanProductSetting.builder().id(productId).saccoId("I1").loanType(LoanType.DEVELOPMENT_LOAN).build();
    @Test void mappingsRequireACompletePairAndEligibleAccountsFromTheSameInstitution(){
        assertThatThrownBy(()->service.configure(product,UUID.randomUUID(),principal,null)).hasMessage("recording.error.productAccountsPair");
        when(repository.valid("I1",principal,"PRINCIPAL")).thenReturn(true);
        assertThatThrownBy(()->service.configure(product,UUID.randomUUID(),principal,interest)).hasMessage("recording.error.productAccountsInvalid");
        assertThat(product.getPrincipalGlAccountId()).isNull();verifyNoInteractions(audit);
        when(repository.valid("I1",interest,"INTEREST")).thenReturn(true);
        service.configure(product,UUID.randomUUID(),principal,interest);
        assertThat(product.getPrincipalGlAccountId()).isEqualTo(principal);assertThat(product.getInterestGlAccountId()).isEqualTo(interest);
        service.configure(product,UUID.randomUUID(),principal,interest);
        verify(audit,times(1)).logEvent(eq("ACCOUNTING"),eq(productId),eq("LOAN_PRODUCT_ACCOUNTS_CONFIGURED"),any(),any(),any(),any(),any(),eq("I1"),isNull(),any());
    }
    @Test void existingLoanLinkCannotMovePrincipalToADifferentProductAccount(){
        var actor=mock(AppUserPrincipal.class);when(actor.getSaccoId()).thenReturn("I1");
        when(repository.binding(actor,productId)).thenReturn(Optional.empty());
        when(repository.product("I1",productId)).thenReturn(Optional.of(Map.of("principal_gl_account_id",principal,"interest_gl_account_id",interest)));
        when(repository.valid(eq("I1"),any(),anyString())).thenReturn(true);
        assertThatThrownBy(()->service.bind(actor,productId,productId,UUID.randomUUID(),"EXISTING_LOAN")).hasMessage("recording.error.productPrincipalMismatch");
        verify(repository,never()).bind(any(),any(),any(),any(),any(),any(),any());verifyNoInteractions(audit);
    }
    @Test void aFrozenLoanRetainsBothAccountsWhenTheProductChanges(){
        var actor=mock(AppUserPrincipal.class);
        when(repository.binding(actor,productId)).thenReturn(Optional.of(Map.of("principal_account_id",principal,"interest_account_id",interest)));
        var accounts=service.bind(actor,productId,UUID.randomUUID(),principal,"EXISTING_LOAN");
        assertThat(accounts.principalId()).isEqualTo(principal);assertThat(accounts.interestId()).isEqualTo(interest);
        verify(repository,never()).product(any(),any());verify(repository,never()).bind(any(),any(),any(),any(),any(),any(),any());
    }
    @Test void lookupRejectsUnboundedPagesAndUnrecognisedCategories(){
        assertThatThrownBy(()->service.choices("I1","PRINCIPAL","",10001,null)).hasMessage("recording.error.filter");
        assertThatThrownBy(()->service.choices("I1","MONEY","",0,null)).hasMessage("recording.error.filter");verifyNoInteractions(repository);
    }
}
