package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.*;
import com.sacco.mvp.accounting.charges.repository.LoanChargeRepository;
import com.sacco.mvp.accounting.charges.service.LoanChargeService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDate;
import java.util.*;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanChargeAccountSummaryTest {
    private final LoanChargeRepository repository=mock(LoanChargeRepository.class);
    private final GeneralLedgerService ledger=mock(GeneralLedgerService.class);
    private final ApplicationClock clock=mock(ApplicationClock.class);
    private final LoanChargeService service=new LoanChargeService(repository,ledger,null,clock,null,null);
    private final UUID product=UUID.randomUUID(),receivable=UUID.randomUUID(),application=UUID.randomUUID(),processing=UUID.randomUUID(),insurance=UUID.randomUUID();
    private final LocalDate today=LocalDate.of(2026,11,17);
    private final AppUserPrincipal actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("INST").stationId("BRANCH").position(Position.ICT_MANAGER).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),Set.of(UserClaim.LOAN_PRODUCTS_VIEW));

    private void availableProduct() {
        when(repository.product("INST",product)).thenReturn(true);when(clock.today()).thenReturn(today);
        when(repository.generatedAccounts("INST",product)).thenReturn(List.of());
    }

    @Test void viewerGetsTheEffectiveMappingsWithoutAccountSearchOrWriteAccess() {
        availableProduct();
        var policy=new Policy(UUID.randomUUID(),product,today.minusDays(1),true,Timing.SEPARATE,Timing.DEDUCT,receivable,null,insurance,"Policy","Agreement","Insurer","Verified",false,null,2,application,processing);
        when(repository.policy("INST",product,today)).thenReturn(Optional.of(policy));
        var labels=Map.of(application,"512001 - Application",processing,"513001 - Processing",receivable,"113001 - Receivable",insurance,"212001 - Insurance");
        when(repository.accountLabels(eq("INST"),anyList())).thenReturn(labels);
        var result=service.accountSummary(actor,product);
        assertThat(result.current()).isSameAs(policy);
        assertThat(result.currentMappings()).containsExactlyInAnyOrderEntriesOf(Map.of("APPLICATION",labels.get(application),"PROCESSING",labels.get(processing),"RECEIVABLE",labels.get(receivable),"INSURANCE",labels.get(insurance)));
        verify(ledger).requireActor(actor,"LOAN_PRODUCTS_VIEW");
        verify(repository).product("INST",product);verify(repository).policy("INST",product,today);
        verify(repository).generatedAccounts("INST",product);verify(repository).accountLabels(eq("INST"),anyList());
        verifyNoMoreInteractions(repository,ledger);
    }

    @Test void legacyMappingsKeepTheirDeferredAccountInsteadOfGeneratedIncomeDefaults() {
        availableProduct();var deferred=UUID.randomUUID();
        var policy=new Policy(UUID.randomUUID(),product,today,true,Timing.SEPARATE,Timing.SEPARATE,receivable,deferred,null,"Old policy","Agreement","","",false,null);
        when(repository.policy("INST",product,today)).thenReturn(Optional.of(policy));
        when(repository.accountLabels(eq("INST"),anyList())).thenReturn(Map.of(deferred,"211001 - Deferred fees",receivable,"113001 - Receivable"));
        when(repository.generatedAccounts("INST",product)).thenReturn(List.of(new GeneratedAccount("APPLICATION",application,"512001 - New income","PENDING")));
        var result=service.accountSummary(actor,product);
        assertThat(result.currentMappings()).containsEntry("APPLICATION","211001 - Deferred fees").containsEntry("PROCESSING","211001 - Deferred fees");
        assertThat(result.generated().getFirst().state()).isEqualTo("PENDING");
    }

    @Test void noEffectivePolicyDoesNotPresentGeneratedAccountsAsCurrentMappings() {
        availableProduct();when(repository.policy("INST",product,today)).thenReturn(Optional.empty());
        when(repository.generatedAccounts("INST",product)).thenReturn(List.of(new GeneratedAccount("PROCESSING",processing,"513001 - Processing","PENDING")));
        var result=service.accountSummary(actor,product);
        assertThat(result.current()).isNull();assertThat(result.currentMappings()).isEmpty();assertThat(result.generated()).hasSize(1);
        verify(repository,never()).accountLabels(anyString(),anyList());
    }

    @Test void foreignProductIsRejectedBeforeLoadingItsMappings() {
        when(repository.product("INST",product)).thenReturn(false);
        assertThatThrownBy(()->service.accountSummary(actor,product)).isInstanceOf(IllegalArgumentException.class).hasMessage("charges.error.policy");
        verify(repository).product("INST",product);verifyNoMoreInteractions(repository);
    }

    @Test void missingOrRevokedViewPermissionStopsBeforeReadingData() {
        doThrow(new AccessDeniedException("Revoked")).when(ledger).requireActor(actor,"LOAN_PRODUCTS_VIEW");
        assertThatThrownBy(()->service.accountSummary(actor,product)).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }

    @Test void summaryAccessDoesNotAllowPolicyProvisioning() {
        doThrow(new AccessDeniedException("Configure required")).when(ledger).requireActor(actor,"LOAN_PRODUCTS_CONFIGURE");
        assertThatThrownBy(()->service.savePolicy(actor,new PolicyForm())).isInstanceOf(AccessDeniedException.class);
        verifyNoInteractions(repository);
    }
}
