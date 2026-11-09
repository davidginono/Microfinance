package com.sacco.mvp.grouplending.service;

import com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Plan;
import com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Term;
import com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Timing;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.grouplending.dto.GroupCycleDtos.*;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.Group;
import com.sacco.mvp.grouplending.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class GroupChargeQuoteTest {
    GroupCycleRepository cycles;LoanRecordingService loans;GroupCycleService service;AppUserPrincipal actor;CycleForm form;
    final UUID id=UUID.randomUUID(),policyId=UUID.randomUUID(),groupId=UUID.randomUUID(),productId=UUID.randomUUID();
    final LocalDate day=LocalDate.of(2026,11,8);
    @BeforeEach void setup(){
        cycles=mock(GroupCycleRepository.class);var groups=mock(GroupLendingRepository.class);loans=mock(LoanRecordingService.class);actor=mock(AppUserPrincipal.class);
        var clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(day);
        var qualification=mock(LoanQualificationPolicyService.class);
        when(qualification.groupPolicy(actor.getSaccoId(),actor.getStationId()))
            .thenReturn(new LoanQualificationPolicyService.GroupQualificationPolicy(1,null,null));
        service=new GroupCycleService(cycles,groups,mock(GeneralLedgerService.class),loans,qualification,clock,mock(AuditService.class),mock(tools.jackson.databind.ObjectMapper.class));
        form=new CycleForm();form.setPolicyId(policyId);form.setApplicationDate(day);form.setFirstPaymentDate(day.plusMonths(1));form.setTenureValue(1);form.setPurpose("Business stock");
        for(var amount:List.of("123.45","876.56")){var r=new ParticipantForm();r.setClientId(UUID.randomUUID());r.setPrincipal(new BigDecimal(amount));r.setIncome(new BigDecimal("10000.00"));r.setExpenses(BigDecimal.ZERO);r.setOtherDebt(BigDecimal.ZERO);r.setAssessmentEvidence("Checked income and expenses");form.getParticipants().add(r);}
        var c=new Cycle(id,groupId,"Group",policyId,productId,Model.SHARED,"DRAFT",0,form,null);when(cycles.cycle(actor,id,false)).thenReturn(c);
        var p=new Policy(policyId,productId,"Product",Model.SHARED,2,50,new BigDecimal("10000.00"),new BigDecimal("10000.00"),1,false,true,"Policy","Agreement",OffsetDateTime.now(),1);when(cycles.policy(actor,policyId)).thenReturn(p);when(cycles.current(p,actor)).thenReturn(true);when(cycles.product(actor,productId)).thenReturn(true);
        var group=mock(Group.class);when(group.status()).thenReturn("ACTIVE");when(groups.group(actor,groupId,false)).thenReturn(group);
        var ids=form.getParticipants().stream().map(ParticipantForm::getClientId).toList();when(cycles.memberships(actor,groupId,ids)).thenReturn(Map.of(ids.getFirst(),UUID.randomUUID(),ids.getLast(),UUID.randomUUID()));
        when(cycles.rules(actor,productId)).thenReturn(new ProductRules(0,new BigDecimal("0.50"),false,BigDecimal.ZERO,false,1));
        var exposure=new Exposure(0,0,0,0,BigDecimal.ZERO,BigDecimal.ZERO,0);when(cycles.exposures(actor,ids)).thenReturn(Map.of(ids.getFirst(),exposure,ids.getLast(),exposure));
        var due=new ScheduleRow(1,day.plusMonths(1),new BigDecimal("1000.01"),new BigDecimal("10.01"),new BigDecimal("1010.02"));
        when(loans.interestQuote(eq(actor),any())).thenReturn(new LoanRecordingService.Quote("Product",BigDecimal.ONE,"FLAT","MONTHLY",due.amount(),due.interest(),List.of(due),"MONTHS",1));
    }
    @Test void sharedQuoteUsesTheCurrentPlanAndConservesChargesDeductionsAndNetCash(){
        var deducted=new Term(UUID.randomUUID(),"MANAGEMENT","FIXED",null,new BigDecimal("10.01"),Timing.DEDUCT,null,null,"Evidence","","");
        var separate=new Term(UUID.randomUUID(),"INSURANCE","FIXED",null,new BigDecimal("5.02"),Timing.SEPARATE,null,null,"Evidence","Insurer","Checked");
        var plan=new Plan(UUID.randomUUID(),"Policy","Agreement",List.of(deducted,separate),true,null);when(loans.chargeQuote(eq(actor),any())).thenReturn(new ChargeQuote(plan,List.of()));
        var quote=service.previewShared(actor,id,form);assertThat(quote.charges()).isEqualByComparingTo("15.03");assertThat(quote.deductions()).isEqualByComparingTo("10.01");assertThat(quote.net()).isEqualByComparingTo("990.00");assertThat(quote.totalPayable()).isEqualByComparingTo("1025.05");
        assertThat(quote.shares().stream().map(SharedShare::charges).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("15.03");assertThat(quote.shares().stream().map(SharedShare::deductions).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("10.01");
        verify(loans,never()).save(any(),any());
    }
    @Test void unsupportedChargePlansStopTheSharedQuote(){
        when(loans.chargeQuote(eq(actor),any())).thenReturn(new ChargeQuote(new Plan(null,"","",List.of(),false,"Unsupported terms"),List.of()));
        assertThatThrownBy(()->service.previewShared(actor,id,form)).hasMessage("group.error.product");verify(loans,never()).save(any(),any());
    }
}
