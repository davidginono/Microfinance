package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.lending.controls.dto.CreditControlDtos.*;
import com.sacco.mvp.lending.controls.service.*;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;

@TestInstance(TestInstance.Lifecycle.PER_CLASS)
@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://(127\\.0\\.0\\.1|localhost):[0-9]+/microfinance_recording_test")
class CreditControlPostgresTest {
    final LoanRecordingPostgresTest f=new LoanRecordingPostgresTest();CreditControlService service;LocalDate day=LocalDate.now(ZoneId.of("Africa/Dar_es_Salaam"));
    @BeforeAll void start(){f.start();service=f.context.getBean(CreditControlService.class);}
    @AfterAll void stop(){f.stop();}
    @BeforeEach void fixture(){f.fixture();when(f.context.getBean(ApplicationClock.class).today()).thenReturn(day);when(f.context.getBean(ApplicationClock.class).now()).thenReturn(day.atTime(12,0).atOffset(ZoneOffset.ofHours(3)));}
    void policy(boolean capacity,boolean collateral){var p=new PolicyForm();p.setExpectedRevision(service.policy(f.actor).revision());p.setCapacityRequired(capacity);p.setCollateralRequired(collateral);p.setVerified(true);p.setReference("Synthetic approved branch policy");service.configure(f.actor,p);}
    UUID pending(){return f.service.save(f.actor,f.form());}
    UUID request(UUID loan,UUID guarantor){var id=UUID.randomUUID();f.jdbc.update("insert into guarantor_requests(id,loan_application_id,guarantor_member_id,guarantor_source,status,created_at,version) values(?,?,?,'LMS','PENDING',now(),0)",id,loan,guarantor);return id;}
    UUID guarantor(){var id=UUID.randomUUID();f.jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,is_member,password_hash,created_at) values(?,?,'B1',?,'Synthetic guarantor','ACTIVE','MEMBER',true,'test-only',now())",id,f.institution,id.toString());return id;}
    AssessmentForm assessment(String cap){var a=new AssessmentForm();a.setIncome(new BigDecimal("2000.00"));a.setExpenses(new BigDecimal("300.00"));a.setOtherDebt(BigDecimal.ZERO);a.setCapacity(new BigDecimal(cap));a.setAssessedOn(day);a.setValidThrough(day.plusDays(30));a.setEvidence("Synthetic independently checked income, expenses and all debts");a.setPolicyReference("Synthetic approved capacity method");a.setVerified(true);return a;}
    AssetForm assetForm(){var a=new AssetForm();a.setCode("ASSET-"+UUID.randomUUID());a.setDescription("Synthetic equipment serial 0123");a.setOwner("Synthetic owner");a.setOwnerEvidence("Synthetic checked ownership and authority");a.setVerified(true);return a;}
    CollateralForm collateral(UUID asset,String kind){var e=new CollateralForm();e.setExpectedSequence(service.asset(f.actor,asset).sequence());e.setKind(kind);e.setEffectiveDate(day);e.setEvidence("Synthetic checked document reference");e.setReason("Synthetic reviewed decision");e.setVerified(true);return e;}
    UUID valued(String value){UUID id=service.createAsset(f.actor,assetForm());var e=collateral(id,"VALUATION");e.setAmount(new BigDecimal(value));e.setEncumbrance(BigDecimal.ZERO);e.setValidThrough(day.plusDays(30));service.collateral(f.actor,id,e);return id;}
    CaseForm caseForm(UUID loan){var c=new CaseForm();c.setLoanId(loan);c.setAssignedTo(f.actor.getMemberId());c.setNextAction(day);c.setReason("Synthetic unpaid instalment follow-up");return c;}
    CollectionForm action(UUID id,String kind){var e=new CollectionForm();e.setExpectedSequence(service.collection(f.actor,id).sequence());e.setKind(kind);e.setEffectiveDate(day);if(!kind.equals("CLOSE"))e.setNextAction(day.plusDays(1));e.setEvidence("Synthetic contact outcome and evidence");return e;}
    @Test void branchPolicyRequiresRealAssessmentsWithoutChangingHistoricalGuarantees(){
        var client=guarantor();var request=request(pending(),client);policy(true,false);
        assertThat(service.guarantee(f.actor,request).capacityError()).isEqualTo("credit.error.assessmentRequired");
        assertThatThrownBy(()->f.jdbc.update("update guarantor_requests set status='APPROVED' where id=?",request)).isInstanceOf(DataAccessException.class);
        var form=assessment("1020.00");UUID id=service.assess(f.actor,request,form);assertThat(service.assess(f.actor,request,form)).isEqualTo(id);
        assertThat(service.guarantee(f.actor,request).capacityError()).isNull();f.jdbc.update("update guarantor_requests set status='APPROVED' where id=?",request);
        assertThat(f.jdbc.queryForObject("select assessment_id from lending_guarantor_capacity_decision where request_id=?",UUID.class,request)).isEqualTo(id);
        var next=request(pending(),client);assertThat(service.guarantee(f.actor,next).capacityError()).isEqualTo("credit.error.capacity");
        assertThatThrownBy(()->f.jdbc.update("delete from lending_guarantor_assessment where id=?",id)).isInstanceOf(DataAccessException.class);
        assertThat(f.jdbc.queryForObject("select lending_capacity_error(?,?,?,?)",String.class,next,f.institution,"B1",day.plusDays(31))).isEqualTo("credit.error.expired");
    }
    @Test void competingOrdinaryGuaranteesCannotSpendTheSameCapacity()throws Exception{
        UUID person=guarantor(),first=request(pending(),person),second=request(pending(),person);policy(true,false);service.assess(f.actor,first,assessment("1500.00"));
        try(var pool=Executors.newFixedThreadPool(2)){var latch=new CountDownLatch(1);var tasks=new ArrayList<Future<Boolean>>();for(var id:List.of(first,second))tasks.add(pool.submit(()->{latch.await();try{new TransactionTemplate(f.context.getBean(PlatformTransactionManager.class)).executeWithoutResult(t->f.jdbc.update("update guarantor_requests set status='APPROVED' where id=?",id));return true;}catch(DataAccessException expected){return false;}}));latch.countDown();assertThat(tasks.get(0).get(45,TimeUnit.SECONDS)^tasks.get(1).get(45,TimeUnit.SECONDS)).isTrue();}
        assertThat(f.jdbc.queryForObject("select count(*) from guarantor_requests where id in (?,?) and status='APPROVED'",Integer.class,first,second)).isEqualTo(1);
    }
    @Test void externalGuaranteesRequireScopedEvidenceAndReserveTheSameIdentity(){
        policy(true,false);var first=request(pending(),null);var second=request(pending(),null);for(var id:List.of(first,second))f.jdbc.update("update guarantor_requests set guarantor_source='EXTERNAL',external_station_id='OTHER',external_member_no='EXT-1',external_full_name='Synthetic external guarantor' where id=?",id);
        service.assess(f.actor,first,assessment("1020.00"));f.jdbc.update("update guarantor_requests set status='APPROVED' where id=?",first);assertThat(service.guarantee(f.actor,second).capacityError()).isEqualTo("credit.error.capacity");
        assertThatThrownBy(()->service.guarantee(f.operator("B2"),first)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void knownInternalDebtCannotDisappearWhenDeclaredDebtIsZero(){
        f.disbursed("D-OWN-COMMITMENT");var form=f.form();form.setClientId(guarantor());UUID target=f.service.save(f.actor,form),request=request(target,f.client);policy(true,false);
        var assessment=assessment("10000.00");assessment.setIncome(new BigDecimal("1000.00"));service.assess(f.actor,request,assessment);
        assertThat(service.guarantee(f.actor,request).capacityError()).isEqualTo("credit.error.capacity");
    }
    @Test void collateralFundingValuationsPledgesAndIndependentReleasePreserveHistory(){
        f.jdbc.update("update loan_product_settings set collateral_required=true,min_collateral_coverage_ratio=1.2 where id=?",f.product);var loan=pending();policy(false,true);
        assertThatThrownBy(()->f.service.disburse(f.actor,f.post(loan,"1000.00","D-COLLATERAL"))).hasMessage("credit.error.collateralRequired");
        UUID asset=valued("1500.00");var pledge=collateral(asset,"PLEDGE");pledge.setLoanId(loan);pledge.setAmount(new BigDecimal("1200.00"));service.collateral(f.actor,asset,pledge);
        assertThat(service.asset(f.actor,asset).pledged()).isEqualByComparingTo("1200.00");f.service.disburse(f.actor,f.post(loan,"1000.00","D-COLLATERAL"));
        var release=collateral(asset,"RELEASE");release.setLoanId(loan);assertThatThrownBy(()->service.collateral(f.actor,asset,release)).hasMessage("credit.error.release");
        var payment=f.post(loan,"1020.00","R-COLLATERAL");payment.setInterestAccountId(f.interest);f.service.repay(f.actor,payment);
        assertThatThrownBy(()->service.collateral(f.actor,asset,release)).hasMessage("credit.error.independent");service.collateral(f.operator("B1"),asset,release);
        assertThat(service.asset(f.actor,asset).pledged()).isZero();assertThat(service.collateralHistory(f.actor,asset,0).rows()).hasSize(3);
        assertThatThrownBy(()->f.jdbc.update("delete from lending_collateral_event where asset_id=?",asset)).isInstanceOf(DataAccessException.class);
        assertThatThrownBy(()->f.jdbc.execute("truncate lending_collateral_event")).isInstanceOf(DataAccessException.class);
    }
    @Test void concurrentPledgesAndStaleFormsCannotOvercommitAnAsset()throws Exception{
        UUID asset=valued("1500.00"),one=pending(),two=pending();var e1=collateral(asset,"PLEDGE");e1.setLoanId(one);e1.setAmount(new BigDecimal("1000.00"));var e2=collateral(asset,"PLEDGE");e2.setLoanId(two);e2.setAmount(new BigDecimal("1000.00"));
        try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);var x=pool.submit(()->{start.await();try{service.collateral(f.actor,asset,e1);return true;}catch(IllegalArgumentException expected){return false;}});var y=pool.submit(()->{start.await();try{service.collateral(f.actor,asset,e2);return true;}catch(IllegalArgumentException expected){return false;}});start.countDown();assertThat(x.get(45,TimeUnit.SECONDS)^y.get(45,TimeUnit.SECONDS)).isTrue();}
        assertThat(service.asset(f.actor,asset).pledged()).isEqualByComparingTo("1000.00");var low=collateral(asset,"VALUATION");low.setAmount(new BigDecimal("999.99"));low.setEncumbrance(BigDecimal.ZERO);low.setValidThrough(day.plusDays(1));service.collateral(f.actor,asset,low);assertThat(service.asset(f.actor,asset).value()).isEqualByComparingTo("999.99");
    }
    @Test void collectionsTrackPartialPaymentsPromisesDisputesAndIndependentReversalsWithoutPostingMoney(){
        UUID loan=f.disbursed("D-COLLECTION");var create=caseForm(loan);UUID id=service.createCase(f.actor,create);assertThat(service.createCase(f.actor,create)).isEqualTo(id);
        when(f.context.getBean(ApplicationClock.class).today()).thenReturn(day.plusDays(5));assertThat(service.createCase(f.actor,create)).isEqualTo(id);when(f.context.getBean(ApplicationClock.class).today()).thenReturn(day);
        assertThatThrownBy(()->service.createCase(f.actor,caseForm(loan))).hasMessage("credit.error.openCase");assertThat(service.collection(f.actor,id).arrears()).isEqualByComparingTo("1020.00");
        var promise=action(id,"PROMISE");promise.setAmount(new BigDecimal("100.00"));promise.setPromiseOn(day.plusDays(7));service.followUp(f.actor,id,promise);
        assertThat(f.service.detail(f.actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
        service.followUp(f.actor,id,action(id,"DISPUTE"));service.followUp(f.actor,id,action(id,"CONTACT"));assertThat(service.collection(f.actor,id).state()).isEqualTo("DISPUTE");service.followUp(f.actor,id,action(id,"RESOLVE"));
        var p=f.post(loan,"100.00","R-COLLECTION");p.setInterestAccountId(f.interest);var posted=f.service.repay(f.actor,p);assertThat(service.collection(f.actor,id).arrears()).isEqualByComparingTo("920.00");
        f.service.reverse(f.operator("B1"),posted.id(),UUID.randomUUID(),day,"Synthetic independent payment correction");assertThat(service.collection(f.actor,id).arrears()).isEqualByComparingTo("1020.00");
        service.followUp(f.actor,id,action(id,"CLOSE"));assertThat(f.service.detail(f.actor,loan).loan().status()).isNotEqualTo("PAID");service.followUp(f.actor,id,action(id,"REOPEN"));
    }
    @Test void instalmentRemindersAreAtomicDailyAndPausedForDisputes(){
        policy(false,false);var settings=new PolicyForm();settings.setExpectedRevision(service.policy(f.actor).revision());settings.setReference("Synthetic approved reminder cadence");settings.setVerified(true);settings.setRemindersEnabled(true);settings.setReminderDaysBefore(7);settings.setReminderEveryDays(1);service.configure(f.actor,settings);UUID loan=f.disbursed("D-REMINDER");UUID id=service.createCase(f.actor,caseForm(loan));service.followUp(f.actor,id,action(id,"DISPUTE"));var reminders=f.context.getBean(InstalmentReminderService.class);reminders.queueBatch();assertThat(reminderCount(loan)).isZero();service.followUp(f.actor,id,action(id,"RESOLVE"));reminders.queueBatch();reminders.queueBatch();assertThat(reminderCount(loan)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("select count(*) from outbox_events o join lending_instalment_reminder r on r.outbox_id=o.id join loan_ledger_installments i on i.id=r.installment_id where i.loan_application_id=? and o.status='NEW'",Integer.class,loan)).isEqualTo(1);
    }
    int reminderCount(UUID loan){return f.jdbc.queryForObject("select count(*) from lending_instalment_reminder r join loan_ledger_installments i on i.id=r.installment_id where i.loan_application_id=? and r.reminder_on=?",Integer.class,loan,day);}
    @Test void freshPermissionsScopeAndAuditRollbackProtectEveryRegistry(){
        UUID asset=valued("1000.00");assertThatThrownBy(()->service.asset(f.operator("B2"),asset)).isInstanceOf(AccessDeniedException.class);
        var all=EnumSet.allOf(UserClaim.class);all.remove(UserClaim.COLLATERAL_VIEW);when(f.context.getBean(UserClaimService.class).effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(all);assertThatThrownBy(()->service.asset(f.actor,asset)).isInstanceOf(AccessDeniedException.class);
        when(f.context.getBean(UserClaimService.class).effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));
        doThrow(new IllegalStateException("Synthetic audit unavailable")).when(f.context.getBean(AuditService.class)).logEvent(anyString(),any(),anyString(),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),anyMap());
        long before=f.jdbc.queryForObject("select count(*) from lending_collateral_asset where sacco_id=?",Long.class,f.institution);assertThatThrownBy(()->service.createAsset(f.actor,assetForm())).hasMessage("Synthetic audit unavailable");assertThat(f.jdbc.queryForObject("select count(*) from lending_collateral_asset where sacco_id=?",Long.class,f.institution)).isEqualTo(before);
    }
}
