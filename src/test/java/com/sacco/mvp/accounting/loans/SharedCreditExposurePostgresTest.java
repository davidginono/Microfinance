package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupCycleDtos.*;
import com.sacco.mvp.grouplending.service.SharedCreditExposureService;
import com.sacco.mvp.repository.*;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SharedCreditExposurePostgresTest {
    final GroupSharedLoanPostgresTest s=new GroupSharedLoanPostgresTest();
    final GroupCyclePostgresTest g=s.s.s.g;
    final LoanRecordingPostgresTest f=g.f;
    SharedCreditExposureService credit;
    @BeforeAll void start(){s.start();credit=f.context.getBean(SharedCreditExposureService.class);}
    @AfterAll void stop(){s.stop();}
    @BeforeEach void setup(){s.setup();}
    com.sacco.mvp.grouplending.dto.SharedCreditExposure own(){return credit.summary(f.institution,f.client,"B1");}
    LoanAnalyticsService analytics(){return new LoanAnalyticsService(f.context.getBean(LoanApplicationRepository.class),mock(ManagerReviewRepository.class),mock(BoardReviewRepository.class),f.context.getBean(LoanProductSettingRepository.class),new AccessControlService(),f.context.getBean(ApplicationClock.class),f.context.getBean(tools.jackson.databind.ObjectMapper.class),credit);}
    LoanQualificationPolicyService qualification(){return new LoanQualificationPolicyService(f.context.getBean(SaccoSettingsRepository.class),f.context.getBean(GuarantorRequestRepository.class),f.context.getBean(MemberRepository.class),mock(ExternalGuarantorRegistryRepository.class),mock(SaccoStationPolicyRepository.class),analytics(),credit);}
    void policy(){f.jdbc.update("insert into sacco_settings(sacco_id,board_quorum,board_size,created_at,updated_at,default_language,external_station_id,max_loan_savings_ratio,required_guarantors,applicant_max_defaulted_loans,guarantor_max_defaulted_loans,guarantor_with_active_loan_allowed) values(?,1,1,now(),now(),'en','B1',0,0,1,1,false)",f.institution);}
    void joint(){var stop=new TerminationForm();stop.setExpectedVersion(1);stop.setReason("Synthetic replacement with joint-and-several terms");g.cycles.withdraw(f.actor,s.s.cycle,stop);s.s.s.rule(SharedLiability.JOINT_AND_SEVERAL);s.s.cycle=s.s.s.submit();s.loan=g.cycles.sharedFacility(f.actor,s.s.cycle).orElseThrow().loanId();
        for(var id:List.of(f.client,g.second))assertThat(g.consents.accept(g.client(id),s.s.cycle,true,"")).isNull();
        String hash=s.s.open();assertThat(s.s.agreements.confirm(s.s.client,s.s.cycle,hash,true,"")).isNull();var other=g.client(g.second);s.s.agreements.exportClient(other,s.s.cycle,Locale.ENGLISH);assertThat(s.s.agreements.confirm(other,s.s.cycle,s.s.agreements.packet(other,s.s.cycle).row().hash(),true,"")).isNull();
    }
    @Test void anUnfundedAcceptedFacilityReservesAnApplicationWithoutInventingPostedDebt(){
        var x=own();assertThat(x.pending()).isEqualTo(1);assertThat(x.active()).isZero();assertThat(x.liability()).isZero();assertThat(x.monthlyPayment()).isZero();assertThat(x.known()).isTrue();
        var product=f.context.getBean(LoanProductSettingRepository.class).findById(f.product).orElseThrow();product.setAllowApplicationWithActiveLoan(true);
        assertThatThrownBy(()->qualification().sharedApplicantExposure(f.institution,f.client,product)).hasMessageContaining("existing loan application");
    }
    @Test void proportionalExposureTracksOnlyTheConservedShareAndLinkedPaymentCorrections(){
        f.service.disburse(f.actor,s.funding());var before=own();assertThat(before.active()).isEqualTo(1);assertThat(before.liability()).isEqualByComparingTo("125.91");assertThat(before.monthlyPayment()).isEqualByComparingTo("125.91");
        var paid=f.service.repay(f.actor,s.payment("120.00"));var member=s.shared.balances(f.actor,s.loan).getFirst();assertThat(own().liability()).isEqualByComparingTo(member.remainingPrincipal().add(member.remainingInterest()).add(member.remainingCharges()));
        assertThat(analytics().activeLoanAmount(f.client,f.institution,"B1")).isEqualByComparingTo(own().liability());
        f.service.reverse(f.operator("B1"),paid.id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic share exposure correction");assertThat(own().liability()).isEqualByComparingTo(before.liability());
        assertThat(f.context.getBean(LoanApplicationRepository.class).countBySaccoIdAndApplicantMemberIdAndStatusIn(f.institution,f.client,List.of(LoanStatus.DISBURSED))).isZero();
    }
    @Test void jointMaximumLiabilityIsTheParentRiskWhileMonthlyPaymentsStayAllocated(){
        joint();f.service.disburse(f.actor,s.funding());var first=own();var other=credit.summary(f.institution,g.second,"B1");
        assertThat(first.liability()).isEqualByComparingTo("1020.01");assertThat(other.liability()).isEqualByComparingTo(first.liability());assertThat(first.monthlyPayment()).isEqualByComparingTo("125.91");assertThat(other.monthlyPayment()).isEqualByComparingTo("894.10");
        f.service.repay(f.actor,s.payment("120.00"));assertThat(own().liability()).isEqualByComparingTo("897.61");
        assertThat(f.jdbc.queryForObject("select sum(interest_excluded) from loan_ledger_installments where loan_application_id=?",BigDecimal.class,s.loan)).isEqualByComparingTo("2.40");
        assertThat(s.count("loan_ledgers")).isEqualTo(1);assertThat(s.count("loan_applications")).isEqualTo(2); // withdrawn source plus current facility
        assertThat(f.jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where sacco_id=? and account_id=?",BigDecimal.class,f.institution,f.principal)).isEqualByComparingTo("880.01");
    }
    @Test void sharedRiskBlocksOrdinaryGuarantorsAndRespectsTheExistingApplicantDefaultLimit(){
        policy();f.service.disburse(f.actor,s.funding());assertThat(qualification().guarantorFailureReason(f.institution,f.client)).hasValueSatisfying(reason->assertThat(reason).contains("active loans"));
        f.jdbc.update("update loan_applications set status='PAR' where id=?",s.loan);assertThat(analytics().defaultedRiskLoanCount(f.client,f.institution,"B1")).isEqualTo(1);
        assertThat(qualification().applicantFailureReason(f.institution,f.client)).hasValueSatisfying(reason->assertThat(reason).contains("defaulted loan count"));
    }
    @Test void ordinaryProductCountsSharedDebtAndAffordabilityUsesTheKnownMonthlyShare(){
        f.service.disburse(f.actor,s.funding());var product=f.context.getBean(LoanProductSettingRepository.class).findById(f.product).orElseThrow();
        assertThatThrownBy(()->g.f.context.getBean(LoanWorkflowService.class).assertCanApplyForProduct(f.institution,f.client,product)).hasMessageContaining("shared-loan obligation");
        product.setAllowApplicationWithActiveLoan(true);product.setMaximumActiveLoans(0);
        assertThatThrownBy(()->g.f.context.getBean(LoanWorkflowService.class).assertCanApplyForProduct(f.institution,f.client,product)).hasMessageContaining("at most 0 existing active loans");
        var eligibility=new EligibilityService(f.context.getBean(LoanProductSettingRepository.class),analytics(),f.context.getBean(tools.jackson.databind.ObjectMapper.class),credit);product.setAffordabilityCheckRequired(true);
        var result=eligibility.check(f.institution,f.client,product,new BigDecimal("1000.00"),Map.of("monthlyIncome",new BigDecimal("1000"),"monthlyExpenses",new BigDecimal("100"),"otherDebtRepayments",BigDecimal.ZERO),Map.of("monthlyRepaymentAmount",new BigDecimal("350")));
        assertThat(result.eligible()).isFalse();assertThat(result.otherDebtRepayments()).isEqualByComparingTo("125.91");
    }
    @Test void groupAssessmentsUseSharedCountsCashFlowAndContingentRiskWithoutDuplicatingLoans(){
        joint();f.service.disburse(f.actor,s.funding());var repository=f.context.getBean(com.sacco.mvp.grouplending.repository.GroupCycleRepository.class);
        var facts=repository.exposures(f.actor,List.of(f.client,g.second));assertThat(facts.get(f.client).activeLoans()).isEqualTo(1);assertThat(facts.get(f.client).monthlyDebt()).isEqualByComparingTo("125.91");assertThat(facts.get(f.client).reserved()).isEqualByComparingTo("894.10");
        assertThat(facts.get(g.second).reserved()).isEqualByComparingTo("125.91");assertThat(facts.get(f.client).pendingApplications()).isZero();
    }
    @Test void firstClassBranchAndTenantScopeSurviveClientMovementWithoutReturningForeignDebt(){
        f.service.disburse(f.actor,s.funding());f.jdbc.update("update members set station_id='B2' where id=?",f.client);
        assertThat(own().liability()).isEqualByComparingTo("125.91");var moved=credit.summary(f.institution,f.client,"B2");assertThat(moved.liability()).isZero();assertThat(moved.known()).isFalse();
        var foreign=credit.summary("FOREIGN",f.client,"B1");assertThat(foreign.active()).isZero();assertThat(foreign.pending()).isZero();assertThat(foreign.liability()).isZero();assertThat(foreign.known()).isTrue();
        var facts=f.context.getBean(com.sacco.mvp.grouplending.repository.GroupCycleRepository.class).exposures(f.operator("B2"),List.of(f.client));assertThat(facts.get(f.client).monthlyDebt()).isZero();assertThat(facts.get(f.client).missingHistory()).isGreaterThan(0);
    }
    @Test void settledAndReturnedFacilitiesReleaseRiskWhileIndependentCorrectionsRestoreIt(){
        f.service.disburse(f.actor,s.funding());var paid=f.service.repay(f.actor,s.payment("1000.01"));assertThat(own().active()).isZero();assertThat(own().liability()).isZero();
        f.service.reverse(f.operator("B1"),paid.id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic settlement correction restores credit obligation");assertThat(own().active()).isEqualTo(1);assertThat(own().liability()).isEqualByComparingTo("125.91");
    }
    @Test void unallocatedNonCashInterestCannotBeReportedAsVerifiedCreditHistory(){
        f.service.disburse(f.actor,s.funding());var clock=f.context.getBean(ApplicationClock.class);when(clock.today()).thenReturn(LoanRecordingPostgresTest.TODAY.plusDays(15));
        f.context.getBean(LoanRepaymentLedgerService.class).waiveInterest(s.loan,f.actor,UUID.randomUUID(),clock.today(),new BigDecimal("1.00"),"Synthetic shared waiver awaiting member attribution",null);
        assertThat(own().known()).isFalse();assertThatThrownBy(()->analytics().activeLoanAmount(f.client,f.institution,"B1")).hasMessageContaining("evidence is incomplete");
    }
    @Test void staffRecordingCannotBypassPendingSharedApplicationsOrActiveProductRules(){
        assertThatThrownBy(()->f.service.save(f.actor,f.form())).hasMessage("recording.error.sharedApplicationPending");
        f.service.disburse(f.actor,s.funding());assertThatThrownBy(()->f.service.save(f.actor,f.form())).hasMessage("recording.error.sharedActiveLoan");
        assertThat(s.count("loan_applications")).isEqualTo(1);
    }
    @Test void staffRecordingFreezesKnownSharedBurdenAndFailsUnaffordableOrOutOfBranchEvidence(){
        f.service.disburse(f.actor,s.funding());f.jdbc.update("update loan_product_settings set allow_application_with_active_loan=true,maximum_active_loans=2,affordability_check_required=true where id=?",f.product);
        var request=f.form();request.setIncome(new BigDecimal("1000"));request.setExpenses(new BigDecimal("100"));request.setOtherDebt(BigDecimal.ZERO);request.setPrincipal(new BigDecimal("350.00"));
        assertThatThrownBy(()->f.service.save(f.actor,request)).hasMessage("recording.error.sharedAffordability");
        request.setPrincipal(new BigDecimal("200.00"));var id=f.service.save(f.actor,request);
        assertThat(f.jdbc.queryForObject("select (policy_snapshot->'sharedCredit'->>'monthlyPayment')::numeric from loan_applications where id=?",BigDecimal.class,id)).isEqualByComparingTo("125.91");
        f.jdbc.update("update members set station_id='B2' where id=?",f.client);
        assertThatThrownBy(()->f.service.save(f.operator("B2"),f.form())).hasMessage("recording.error.sharedCreditHistory");
    }
    @Test void staffIntakeRacingSharedSubmissionCannotReserveBothApplications()throws Exception{
        var closed=new TerminationForm();closed.setExpectedVersion(1);closed.setReason("Synthetic clean admission race");g.cycles.withdraw(f.actor,s.s.cycle,closed);
        var form=s.s.s.form();UUID cycle=g.cycles.create(f.actor,g.group,form);var individual=f.form();
        try(var workers=java.util.concurrent.Executors.newFixedThreadPool(2)){
            var start=new java.util.concurrent.CountDownLatch(1);
            var group=workers.submit(()->{start.await();try{g.cycles.submit(f.actor,cycle,form);return "GROUP";}catch(IllegalArgumentException|IllegalStateException e){return e.getMessage();}});
            var person=workers.submit(()->{start.await();try{f.service.save(f.actor,individual);return "PERSON";}catch(IllegalArgumentException|IllegalStateException e){return e.getMessage();}});
            start.countDown();var results=List.of(group.get(60,java.util.concurrent.TimeUnit.SECONDS),person.get(60,java.util.concurrent.TimeUnit.SECONDS));
            assertThat(results.stream().filter(x->Set.of("GROUP","PERSON").contains(x)).count()).isEqualTo(1);
            assertThat(results.stream().filter(x->x.endsWith("sharedApplicationPending")).count()).isEqualTo(1);
        }
        assertThat(f.jdbc.queryForObject("select count(*) from loan_applications where sacco_id=? and status<>'WITHDRAWN'",Integer.class,f.institution)).isEqualTo(1);
        assertThat(s.count("loan_ledgers")).isZero();assertThat(s.count("gl_journal")).isZero();
    }
}
