package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.*;
import com.sacco.mvp.accounting.charges.repository.LoanChargeRepository;
import com.sacco.mvp.accounting.charges.service.LoanChargeService;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanChargeMappingsTest {
    final LoanChargeRepository repository=mock(LoanChargeRepository.class);
    final GeneralLedgerService ledger=mock(GeneralLedgerService.class);
    final ApplicationClock clock=mock(ApplicationClock.class);
    final LoanChargeService service=new LoanChargeService(repository,ledger,null,clock,mock(AuditService.class),tools.jackson.databind.json.JsonMapper.builder().build());
    final UUID product=UUID.randomUUID(),priorId=UUID.randomUUID(),saved=UUID.randomUUID();
    final LocalDate today=LocalDate.of(2026,11,17);
    final AppUserPrincipal actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1")
        .position(Position.ICT_MANAGER).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),Set.of(UserClaim.LOAN_PRODUCTS_CONFIGURE,UserClaim.ACCOUNTING_ACCOUNTS_UPDATE));
    final Policy prior=new Policy(priorId,product,today.minusDays(1),false,Timing.DEDUCT,Timing.SEPARATE,UUID.randomUUID(),null,UUID.randomUUID(),
        "Historical policy","Historical agreement","Historical insurer","Historical verification",true,new java.math.BigDecimal("5000"),2,UUID.randomUUID(),UUID.randomUUID());
    @BeforeEach void setup(){
        when(clock.today()).thenReturn(today);when(clock.now()).thenReturn(today.atStartOfDay().atOffset(ZoneOffset.UTC));
        when(repository.product("I1",product)).thenReturn(true);
        when(repository.policy(eq("I1"),eq(product),any())).thenReturn(Optional.of(prior));
        when(repository.productChoice("I1",product)).thenReturn(new Choice(product,"Loan",""));
        when(repository.generatedAccounts("I1",product)).thenReturn(List.of());
        when(repository.insertPolicy(eq(actor),any(),anyString(),any())).thenReturn(saved);
    }
    AccountMappingForm form(){var f=new AccountMappingForm();f.setProductId(product);f.setExpectedVersion(priorId);
        f.setApplicationAccountId(UUID.randomUUID());f.setProcessingAccountId(UUID.randomUUID());f.setReceivableAccountId(UUID.randomUUID());f.setInsuranceAccountId(UUID.randomUUID());return f;}
    @Test void accountSaveRetainsAllExistingFinancialSettingsOnTheServer(){
        var f=form();assertThat(service.saveAccountMappings(actor,f)).isEqualTo(saved);
        verify(repository).insertPolicy(eq(actor),argThat(p->!p.isEnabled() && p.getManagementTiming()==Timing.DEDUCT && p.getInsuranceTiming()==Timing.SEPARATE
            && p.getPolicyReference().equals(prior.policyReference()) && p.getAgreementReference().equals(prior.agreementReference())
            && p.getInsurer().equals(prior.insurer()) && p.getInsurerVerification().equals(prior.insurerVerification())
            && p.isAllowCustom() && p.getCustomManagementLimit().compareTo(prior.customManagementLimit())==0
            && p.getApplicationAccountId().equals(f.getApplicationAccountId()) && p.getProcessingAccountId().equals(f.getProcessingAccountId())
            && p.getReceivableAccountId().equals(f.getReceivableAccountId()) && p.getInsuranceAccountId().equals(f.getInsuranceAccountId())),anyString(),any());
        for(String kind:List.of("APPLICATION","PROCESSING","RECEIVABLE","INSURANCE"))verify(repository).account(eq("I1"),any(),eq(kind));
    }
    @Test void replayReturnsTheRecordedResultBeforeReadingChangedDefaults(){
        var f=form();var recorded=new HashMap<UUID,String>();
        when(repository.policyRetry(eq(actor),any(),anyString())).thenAnswer(c->{String hash=recorded.get(c.getArgument(1));if(hash==null)return Optional.empty();assertThat(c.<String>getArgument(2)).isEqualTo(hash);return Optional.of(saved);});
        when(repository.insertPolicy(eq(actor),any(),anyString(),any())).thenAnswer(c->{recorded.put(c.<PolicyForm>getArgument(1).getRequestKey(),c.getArgument(2));return saved;});
        assertThat(service.saveAccountMappings(actor,f)).isEqualTo(saved);clearInvocations(repository);
        assertThat(service.saveAccountMappings(actor,f)).isEqualTo(saved);
        verify(repository,never()).policy(anyString(),any(),any());verify(repository,never()).insertPolicy(any(),any(),anyString(),any());
    }
    @Test void staleOrForeignMappingRequestsCannotWrite(){
        var stale=form();stale.setExpectedVersion(UUID.randomUUID());
        assertThatThrownBy(()->service.saveAccountMappings(actor,stale)).hasMessage("charges.error.stale");
        when(repository.product("I1",product)).thenReturn(false);
        assertThatThrownBy(()->service.saveAccountMappings(actor,form())).hasMessage("charges.error.policy");
        verify(repository,never()).insertPolicy(any(),any(),anyString(),any());
    }
    @Test void failedMappingDraftRetainsItsSelectionsAndRequestIdentity(){
        var f=form();var result=service.accountEditor(actor,product,f);
        assertThat(result.form().getApplicationAccountId()).isEqualTo(f.getApplicationAccountId());
        assertThat(result.form().getInsuranceAccountId()).isEqualTo(f.getInsuranceAccountId());
        assertThat(result.form().getRequestKey()).isEqualTo(f.getRequestKey());assertThat(result.form().isEnabled()).isFalse();
        verify(repository,never()).insertPolicy(any(),any(),anyString(),any());
    }
    @Test void mappingRequestHasNoPolicyReferenceCollectionOrActivationFields()throws Exception{
        var names=Arrays.stream(java.beans.Introspector.getBeanInfo(AccountMappingForm.class).getPropertyDescriptors()).map(java.beans.PropertyDescriptor::getName).toList();
        assertThat(names).doesNotContain("enabled","effectiveFrom","managementTiming","insuranceTiming","policyReference","agreementReference","insurer","insurerVerification","allowCustom","customManagementLimit");
    }
}
