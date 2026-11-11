package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.dto.GroupCycleDtos.*;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.*;
import com.sacco.mvp.grouplending.repository.*;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupCyclePostgresTest {
    final LoanRecordingPostgresTest f=new LoanRecordingPostgresTest();
    GroupLendingService groups;GroupCycleService cycles;UUID group,second,policy;
    @BeforeAll void start(){start(Config.class);}
    void start(Class<?> configuration){f.context=new AnnotationConfigApplicationContext(configuration);f.jdbc=f.context.getBean(JdbcTemplate.class);f.service=f.context.getBean(LoanRecordingService.class);
        groups=f.context.getBean(GroupLendingService.class);cycles=f.context.getBean(GroupCycleService.class);}
    @AfterAll void stop(){f.stop();}
    @BeforeEach void setup(){
        reset(f.context.getBean(StationOtpSettingsService.class));
        reset(f.context.getBean(StationOtpDeliveryService.class));
        f.context.getBean(org.springframework.mock.env.MockEnvironment.class).setProperty("app.auth.otp.ttl-minutes","10");
        var clock=f.context.getBean(ApplicationClock.class);reset(clock);when(clock.today()).thenReturn(LoanRecordingPostgresTest.TODAY);when(clock.now()).thenReturn(java.time.OffsetDateTime.parse("2026-10-08T12:00:00+03:00"));
        f.fixture();f.jdbc.update("update loan_product_settings set interest_method='REDUCING_BALANCE',guarantors_required=1,max_repayment_to_disposable_income_ratio=0.4 where id=?",f.product);
        second=UUID.randomUUID();f.jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,is_member,password_hash,created_at) values(?,?,'B1',?,'Second synthetic client','ACTIVE','MEMBER',true,'test-only',now())",second,f.institution,second.toString());
        var gf=new GroupForm();gf.setName("Synthetic cycle group");gf.setEvidence("Synthetic identity records");group=groups.create(f.actor,gf);
        for(var id:List.of(f.client,second)){f.jdbc.update("update members set signature_text='Synthetic personal signature',signature_registered_at=now() where id=?",id);var mf=new MemberForm();mf.setClientId(id);mf.setStartsOn(LocalDate.of(2026,9,1));mf.setEvidence("Synthetic membership agreement");groups.addMember(f.actor,group,mf);}
        policy=cycles.configure(f.actor,policy());
    }
    PolicyForm policy(){var p=new PolicyForm();p.setProductId(f.product);p.setModel(Model.INDIVIDUAL);p.setMinMembers(2);p.setMaxMembers(5);p.setMaxPrincipal(new BigDecimal("10000.00"));p.setMaxGuarantee(new BigDecimal("5000.00"));p.setMaxActiveCycles(1);p.setOwnLoanGuarantors(true);p.setEnabled(true);p.setPolicyReference("Synthetic approved group credit policy");p.setAgreementReference("Synthetic approved bilingual agreement");return p;}
    CycleForm form(){var c=new CycleForm();c.setPolicyId(policy);c.setApplicationDate(LocalDate.of(2026,9,1));c.setFirstPaymentDate(LocalDate.of(2026,10,1));c.setTenureValue(1);c.setPurpose("Business stock");
        for(var id:List.of(f.client,second)){var r=new ParticipantForm();r.setClientId(id);r.setPrincipal(new BigDecimal("1000.00"));r.setIncome(new BigDecimal("10000.00"));r.setExpenses(new BigDecimal("1000.00"));r.setOtherDebt(BigDecimal.ZERO);r.setGuaranteeCapacity(new BigDecimal("5000.00"));r.setAssessmentEvidence("Synthetic verified income and expenses");r.setCapacityEvidence("Synthetic verified guarantee capacity");c.getParticipants().add(r);}return c;}
    @Test void sharedPreviewUsesOneParentAndConservesUnequalComponentsWithoutCreatingDebt(){
        var p=policy();p.setModel(Model.SHARED);p.setSharedLiability(SharedLiability.PROPORTIONAL);policy=cycles.configure(f.actor,p);var form=form();
        form.getParticipants().get(0).setPrincipal(new BigDecimal("123.45"));form.getParticipants().get(1).setPrincipal(new BigDecimal("876.56"));
        UUID id=cycles.create(f.actor,group,form);var quote=cycles.previewShared(f.actor,id,form);
        assertThat(quote.principal()).isEqualByComparingTo("1000.01");
        assertThat(quote.shares().stream().map(SharedShare::principal).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(quote.principal());
        assertThat(quote.shares().stream().map(SharedShare::interest).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(quote.interest());
        assertThat(quote.shares().stream().map(SharedShare::charges).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(quote.charges());
        assertThat(quote.shares().stream().map(SharedShare::net).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(quote.net());
        assertThat(f.jdbc.queryForObject("select count(*) from loan_applications where sacco_id=?",Integer.class,f.institution)).isZero();
        assertThat(cycles.detail(f.actor,id).state()).isEqualTo("DRAFT");
    }
    @Test void sharedPreviewCannotUseForeignPersonMissingAssessmentOrFailedAffordability(){
        var p=policy();p.setModel(Model.SHARED);p.setSharedLiability(SharedLiability.PROPORTIONAL);policy=cycles.configure(f.actor,p);var form=form();UUID id=cycles.create(f.actor,group,form);
        form.getParticipants().get(0).setExpenses(null);
        assertThatIllegalArgumentException().isThrownBy(()->cycles.previewShared(f.actor,id,form)).withMessage("group.error.amount");
        form.getParticipants().get(0).setExpenses(new BigDecimal("9999.00"));
        assertThatIllegalArgumentException().isThrownBy(()->cycles.previewShared(f.actor,id,form)).withMessage("group.error.affordability");
        form.getParticipants().get(0).setClientId(UUID.randomUUID());
        assertThatIllegalArgumentException().isThrownBy(()->cycles.previewShared(f.actor,id,form)).withMessage("group.error.membership");
    }
    @Test void sharedPreviewRejectsAFormerBranchClientWithoutRewritingMembership(){
        var p=policy();p.setModel(Model.SHARED);p.setSharedLiability(SharedLiability.PROPORTIONAL);policy=cycles.configure(f.actor,p);var form=form();UUID id=cycles.create(f.actor,group,form);
        f.jdbc.update("update members set station_id='B2' where id=?",second);
        assertThatIllegalArgumentException().isThrownBy(()->cycles.previewShared(f.actor,id,form)).withMessage("group.error.membership");
        assertThat(f.jdbc.queryForObject("select count(*) from lending_group_member where group_id=? and client_id=?",Integer.class,group,second)).isEqualTo(1);
    }
    AppUserPrincipal client(UUID id){var m=Member.builder().id(id).saccoId(f.institution).stationId("B1").memberNo(id.toString()).fullName("Synthetic participant").status(MemberStatus.ACTIVE).position(Position.MEMBER).memberAccount(true).signatureText("Synthetic personal signature").email("synthetic@example.invalid").build();
        when(f.context.getBean(MemberDirectoryService.class).find(id)).thenReturn(Optional.of(m));return com.sacco.mvp.config.MvcSecurityTestConfiguration.formerClient(m,EnumSet.allOf(UserClaim.class));}
    @Test void submissionFreezesTwoLoansAndGuaranteesWithoutPostingMoney(){
        var form=form();UUID id=cycles.create(f.actor,group,form);assertThat(cycles.create(f.actor,group,form)).isEqualTo(id);cycles.submit(f.actor,id,form);
        cycles.submit(f.actor,id,form);
        assertThat(cycles.detail(f.actor,id).state()).isEqualTo("SUBMITTED");assertThat(cycles.participants(f.actor,id)).hasSize(2);
        assertThat(f.jdbc.queryForObject("select count(*) from lending_group_guarantee where cycle_id=?",Integer.class,id)).isEqualTo(2);
        assertThat(f.jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,f.institution)).isZero();
        assertThatThrownBy(()->cycles.save(f.actor,id,form)).hasMessage("group.error.changed");
        assertThatThrownBy(()->f.jdbc.update("update lending_group_participant set principal=999 where cycle_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void concurrentSubmissionsCannotReserveTheSameCapacityTwice()throws Exception{
        var first=form();var next=form();UUID one=cycles.create(f.actor,group,first),two=cycles.create(f.actor,group,next);var start=new CountDownLatch(1);
        try(var workers=Executors.newFixedThreadPool(2)){
            var tasks=List.of(workers.submit(()->submitAfter(start,one,first)),workers.submit(()->submitAfter(start,two,next)));start.countDown();
            assertThat(List.of(tasks.getFirst().get(60,TimeUnit.SECONDS),tasks.getLast().get(60,TimeUnit.SECONDS))).containsExactlyInAnyOrder("submitted","group.error.activeCycles");
        }
        assertThat(f.jdbc.queryForObject("select count(*) from loan_applications where sacco_id=?",Integer.class,f.institution)).isEqualTo(2);
    }
    String submitAfter(CountDownLatch start,UUID id,CycleForm form)throws Exception{start.await();try{cycles.submit(f.actor,id,form);return "submitted";}catch(IllegalArgumentException e){return e.getMessage();}}

    @Test void branchDefaultLimitsApplyToGroupApplicants(){
        UUID existing=f.service.save(f.actor,f.form());f.service.disburse(f.actor,f.post(existing,"1000.00","D-EXISTING"));
        f.jdbc.update("update loan_applications set status='PAR' where id=?",existing);
        f.jdbc.update("insert into sacco_settings(sacco_id,board_quorum,board_size,created_at,updated_at,default_language,external_station_id,max_loan_savings_ratio,required_guarantors,applicant_max_defaulted_loans) values(?,1,1,now(),now(),'en','B1',0,1,1)",f.institution);
        var form=form();UUID id=cycles.create(f.actor,group,form);assertThatThrownBy(()->cycles.submit(f.actor,id,form)).hasMessage("group.error.defaultPolicy");
    }

    @Test void insufficientCapacityAndStalePolicyCreateNoPartialLoans(){
        var form=form();form.getParticipants().getFirst().setGuaranteeCapacity(new BigDecimal("100.00"));UUID id=cycles.create(f.actor,group,form);
        assertThatThrownBy(()->cycles.submit(f.actor,id,form)).hasMessage("group.error.guaranteeCapacity");
        assertThat(f.jdbc.queryForObject("select count(*) from loan_applications where sacco_id=?",Integer.class,f.institution)).isZero();
        cycles.configure(f.actor,policy());assertThatThrownBy(()->cycles.submit(f.actor,id,form)).hasMessage("group.error.policy");
        assertThatThrownBy(()->cycles.detail(f.operator("B2"),id)).isInstanceOf(AccessDeniedException.class);
    }
    UUID submitted(){var form=form();var id=cycles.create(f.actor,group,form);cycles.submit(f.actor,id,form);return id;}
    void requireOtp(){when(f.context.getBean(StationOtpSettingsService.class).requiresApprovalOtp(any(),anyString(),anyString())).thenReturn(true);}

    @Test void auditFailureRollsBackEveryCreatedLoanAndGuarantee(){
        var form=form();UUID id=cycles.create(f.actor,group,form);
        doThrow(new IllegalStateException("Synthetic audit failure")).when(f.context.getBean(AuditService.class)).logEvent(eq("GROUP_LENDING"),any(),eq("GROUP_CYCLE_SUBMITTED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->cycles.submit(f.actor,id,form)).hasMessage("Synthetic audit failure");
        assertThat(cycles.detail(f.actor,id).state()).isEqualTo("DRAFT");
        assertThat(f.jdbc.queryForObject("select count(*) from loan_applications where sacco_id=?",Integer.class,f.institution)).isZero();
        assertThat(f.jdbc.queryForObject("select count(*) from lending_group_guarantee where cycle_id=?",Integer.class,id)).isZero();
    }
    @Configuration(proxyBeanMethods=false) @Import(GroupLendingPostgresTest.Config.class)
    static class Config {
        @Bean GroupPaperRepository groupPapers(JdbcTemplate j){return new GroupPaperRepository(j);}
        @Bean GroupPaperService groupPaperService(GroupPaperRepository r,GeneralLedgerService a,org.springframework.transaction.PlatformTransactionManager tx,ApplicationClock c,AuditService audit,ObjectMapper json){return new GroupPaperService(r,a,tx,c,audit,json);}
        @Bean GroupCycleRepository cycles(JdbcTemplate jdbc,ObjectMapper json){return new GroupCycleRepository(jdbc,json);}
        @Bean LoanQualificationPolicyService qualification(SaccoSettingsRepository settings,MemberRepository members,GuarantorRequestRepository guarantors,com.sacco.mvp.grouplending.service.SharedCreditExposureService shared){return new LoanQualificationPolicyService(settings,guarantors,members,mock(ExternalGuarantorRegistryRepository.class),mock(SaccoStationPolicyRepository.class),mock(LoanAnalyticsService.class),shared);}
        @Bean LoanWorkflowService borrowerWorkflow(LoanProductSettingRepository products,LoanApplicationRepository applications,GuarantorRequestRepository guarantors,MemberRepository members,SaccoSettingsRepository settings,ObjectMapper json,LoanQualificationPolicyService policies,AuditService audit,org.springframework.transaction.PlatformTransactionManager transactions,ApplicationClock clock){
            var eligibility=mock(EligibilityService.class);when(eligibility.check(anyString(),any(),any(LoanProductSetting.class),any(),anyMap(),anyMap())).thenReturn(new EligibilityService.EligibilityResult(true,BigDecimal.ONE,BigDecimal.TEN,BigDecimal.TEN));
            return new LoanWorkflowService(applications,members,mock(FormSchemaService.class),mock(LoanAttachmentService.class),json,mock(ApplicationNumberService.class),audit,transactions,clock);
        }
        @Bean GroupCycleService cycleService(GroupCycleRepository r,GroupLendingRepository g,GeneralLedgerService access,LoanRecordingService loans,LoanQualificationPolicyService qualification,ApplicationClock clock,AuditService audit,ObjectMapper json){return new GroupCycleService(r,g,access,loans,qualification,clock,audit,json);}
        @Bean StationOtpSettingsService otpSettings(){return mock(StationOtpSettingsService.class);}
        @Bean org.springframework.mock.env.MockEnvironment consentEnvironment(){return new org.springframework.mock.env.MockEnvironment();}
        @Bean StationOtpDeliveryService transport(){return mock(StationOtpDeliveryService.class);}

    }
}
