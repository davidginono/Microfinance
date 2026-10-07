package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.AccountingLibraryDtos.*;
import com.sacco.mvp.accounting.dto.GeneralLedgerDtos.AccountCommand;
import com.sacco.mvp.accounting.policy.AccountingPolicyService;
import com.sacco.mvp.accounting.repository.*;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.*;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.TransactionTemplate;
import java.time.OffsetDateTime;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_LIBRARY_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_library_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingCodeLibraryPostgresTest {
    JdbcTemplate jdbc;TransactionTemplate tx;AccountingCodeLibraryService service;GeneralLedgerService ledger;
    AuditService audit;UserClaimService claims;MemberDirectoryService directory;SaccoRegistryService institutions;
    AppUserPrincipal actor;String institution;UUID cash,expense,control;
    @BeforeAll void database() {
        var ds=new DriverManagerDataSource(System.getenv("MICROFINANCE_LIBRARY_DATABASE_URL"),"microfinance_test","");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();jdbc=new JdbcTemplate(ds);
        var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
        audit=mock(AuditService.class);claims=mock(UserClaimService.class);directory=mock(MemberDirectoryService.class);institutions=mock(SaccoRegistryService.class);
        var clock=mock(ApplicationClock.class);when(clock.now()).thenReturn(OffsetDateTime.now());
        ledger=proxy(new GeneralLedgerService(new GeneralLedgerRepository(jdbc),mock(AccountingPolicyService.class),new AccessControlService(),audit,clock,claims,directory,institutions,mock(com.sacco.mvp.reporting.execution.service.AccountingReleaseGateService.class)),manager);
        service=proxy(new AccountingCodeLibraryService(new AccountingCodeLibraryRepository(jdbc),ledger,audit,clock),manager);
    }
    @SuppressWarnings("unchecked") private <T>T proxy(T target,DataSourceTransactionManager manager) {
        var factory=new ProxyFactory(target);factory.setProxyTargetClass(true);factory.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));return (T)factory.getProxy();
    }
    @BeforeEach void fixture() {
        reset(audit,claims,directory,institutions);institution="LIBRARY-"+UUID.randomUUID();
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,'Synthetic library',true,now(),now())",institution);
        var member=Member.builder().id(UUID.randomUUID()).saccoId(institution).stationId("B1").memberNo(UUID.randomUUID().toString()).fullName("Synthetic accountant").position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build();
        jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,staff_access_status,created_at,is_member,password_hash) values(?,?,'B1',?,'Synthetic accountant','ACTIVE','ACCOUNTANT','ACTIVE',now(),false,'test-only')",member.getId(),institution,member.getMemberNo());
        actor=new AppUserPrincipal(member,EnumSet.allOf(UserClaim.class),true);when(directory.find(member.getId())).thenReturn(Optional.of(member));
        when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));
        when(institutions.findActiveSacco(institution)).thenReturn(Optional.of(RegisteredSacco.builder().saccoId(institution).active(true).build()));
        when(institutions.findStation(institution,"B1")).thenReturn(Optional.of(SaccoStation.builder().saccoId(institution).stationId("B1").active(true).accessStatus(SaccoAccessStatus.ACTIVE).build()));
        cash=ledger.createAccount(actor,new AccountCommand("CASH","Cash","ASSET","DEBIT","POSTING","CASH",null));
        expense=ledger.createAccount(actor,new AccountCommand("EXPENSE","Office expense","EXPENSE","DEBIT","POSTING","EXPENSE",null));
        control=ledger.createAccount(actor,new AccountCommand("PRINCIPAL","Principal","ASSET","DEBIT","CONTROL","LOAN_PRINCIPAL",null));
    }
    @Test void activityOwnsMultipleCodesAndVersionHistoryIsImmutable() {
        var activity=service.createActivity(actor,code("ops",null));
        UUID first=service.createTransaction(actor,code("CASH-EXPENSE",activity)),second=service.createTransaction(actor,code("BANK-EXPENSE",activity));
        assertThat(service.transactions(actor,activity,"","",0).rows()).extracting(TransactionCode::id).containsExactlyInAnyOrder(first,second);
        var form=template(0,"EXPENSE","CASH");UUID one=service.saveTemplate(actor,first,form);
        assertThat(service.saveTemplate(actor,first,form)).isEqualTo(one);
        var next=template(1,"CASH","EXPENSE");UUID two=service.saveTemplate(actor,first,next);
        assertThat(service.template(actor,first,one).rules().getFirst().debitCode()).isEqualTo("EXPENSE");
        assertThat(service.template(actor,first,null).id()).isEqualTo(two);
        assertThat(service.versions(actor,first,0).rows()).extracting(Version::version).containsExactly(2,1);
        assertThatThrownBy(()->jdbc.update("update gl_template_line set account_id=? where template_id=?",cash,one)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("delete from gl_template_version where id=?",one)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("insert into gl_template_line(template_id,sacco_id,component,side,account_id) values(?,?,'TAX','DEBIT',?)",one,institution,cash)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,institution)).isZero();
    }
    @Test void validatesComponentsAccountsAndManualControlBoundary() {
        UUID activity=service.createActivity(actor,code("OPERATIONS",null)),transaction=service.createTransaction(actor,code("PAY",activity));
        var totalAndPrincipal=template(0,"EXPENSE","CASH");var principal=new RuleForm();principal.setComponent("PRINCIPAL");principal.setDebitCode("PRINCIPAL");principal.setCreditCode("CASH");totalAndPrincipal.getRules().add(principal);
        assertThatThrownBy(()->service.saveTemplate(actor,transaction,totalAndPrincipal)).hasMessage("library.error.totalOverlap");
        assertThatThrownBy(()->service.saveTemplate(actor,transaction,template(0,"PRINCIPAL","CASH"))).hasMessage("library.error.manualControl");
        assertThatThrownBy(()->service.saveTemplate(actor,transaction,template(0,"MISSING","CASH"))).hasMessage("library.error.templateAccount");
        ledger.deactivateAccount(actor,expense);
        assertThatThrownBy(()->service.saveTemplate(actor,transaction,template(0,"EXPENSE","CASH"))).hasMessage("library.error.templateAccount");
        ledger.reactivateAccount(actor,expense);
        var specialized=code("REPAY",activity);specialized.setSourceEvent("REPAYMENT");UUID repay=service.createTransaction(actor,specialized);
        var parts=template(0,"PRINCIPAL","CASH");parts.getRules().getFirst().setComponent("PRINCIPAL");service.saveTemplate(actor,repay,parts);
    }
    @Test void scopesAllRecordsAndChecksCurrentClaims() {
        UUID activity=service.createActivity(actor,code("PRIVATE",null)),transaction=service.createTransaction(actor,code("PRIVATE-PAY",activity));
        assertThatThrownBy(()->service.activity(actor,UUID.randomUUID())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->service.transaction(actor,UUID.randomUUID())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->service.template(actor,transaction,UUID.randomUUID())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        String foreign="FOREIGN-"+UUID.randomUUID();UUID foreignId=UUID.randomUUID();
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,'Synthetic foreign',true,now(),now())",foreign);
        jdbc.update("insert into gl_activity(id,sacco_id,code,name,created_by,created_at) values(?,?,'FOREIGN','Foreign',?,now())",foreignId,foreign,actor.getMemberId());
        assertThatThrownBy(()->service.activity(actor,foreignId)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->service.createTransaction(actor,code("FOREIGN-PAY",foreignId))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(Set.of());
        assertThatThrownBy(()->service.createActivity(actor,code("REVOKED",null))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->service.activities(actor,"","",0)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void lifecycleProtectsParentsAndKeepsHistory() {
        UUID activity=service.createActivity(actor,code("OPS",null)),transaction=service.createTransaction(actor,code("PAY",activity));
        UUID version=service.saveTemplate(actor,transaction,template(0,"EXPENSE","CASH"));
        assertThatThrownBy(()->service.activityState(actor,activity,false)).hasMessage("library.error.activeTransactions");
        service.transactionState(actor,transaction,false);service.activityState(actor,activity,false);
        assertThatThrownBy(()->service.transactionState(actor,transaction,true)).hasMessage("library.error.inactiveActivity");
        assertThat(service.template(actor,transaction,version)).isNotNull();
        service.activityState(actor,activity,true);service.transactionState(actor,transaction,true);
        assertThatThrownBy(()->jdbc.update("delete from gl_transaction_code where id=?",transaction)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void auditFailureRollsBackAndDuplicateCodesStayUnique() {
        UUID activity=service.createActivity(actor,code("OPS",null)),transaction=service.createTransaction(actor,code("PAY",activity));
        doThrow(new IllegalStateException("Audit failed")).when(audit).logEvent(anyString(),any(),anyString(),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),anyMap());
        assertThatThrownBy(()->service.saveTemplate(actor,transaction,template(0,"EXPENSE","CASH"))).isInstanceOf(IllegalStateException.class);
        assertThat(service.transaction(actor,transaction).revision()).isZero();assertThat(service.versions(actor,transaction,0).rows()).isEmpty();reset(audit);
        assertThatThrownBy(()->service.createActivity(actor,code("ops",null))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->service.createTransaction(actor,code("PAY",activity))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void concurrentRetriesSaveOnceAndStaleWritesCannotReplaceHistory() throws Exception {
        UUID activity=service.createActivity(actor,code("OPS",null)),transaction=service.createTransaction(actor,code("PAY",activity));
        UUID key=UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=template(0,"EXPENSE","CASH");a.setRequestKey(key);var b=template(0,"EXPENSE","CASH");b.setRequestKey(key);
            var start=new CountDownLatch(1);var first=pool.submit(()->{start.await();return service.saveTemplate(actor,transaction,a);});var second=pool.submit(()->{start.await();return service.saveTemplate(actor,transaction,b);});start.countDown();
            assertThat(first.get(20,TimeUnit.SECONDS)).isEqualTo(second.get(20,TimeUnit.SECONDS));
        }
        assertThat(service.versions(actor,transaction,0).rows()).hasSize(1);
        assertThatThrownBy(()->service.saveTemplate(actor,transaction,template(0,"CASH","EXPENSE"))).hasMessage("library.error.staleTemplate");
        var changed=template(0,"CASH","EXPENSE");changed.setRequestKey(key);
        assertThatThrownBy(()->service.saveTemplate(actor,transaction,changed)).hasMessage("library.error.changedRetry");
    }
    @Test void databaseRejectsUnbalancedTemplatesAndForeignOwnership() {
        UUID activity=service.createActivity(actor,code("OPS",null)),transaction=service.createTransaction(actor,code("PAY",activity));
        assertThatThrownBy(()->tx.executeWithoutResult(status->{
            UUID v=UUID.randomUUID();jdbc.update("insert into gl_template_version(id,sacco_id,transaction_id,version,source_event,reason,request_key,payload_hash,created_by,created_at) values(?,?,?,1,'MANUAL_JOURNAL','Synthetic',?,'hash',?,now())",v,institution,transaction,UUID.randomUUID(),actor.getMemberId());
            jdbc.update("insert into gl_template_line(template_id,sacco_id,component,side,account_id) values(?,?,'TOTAL','DEBIT',?)",v,institution,expense);
        })).hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class).hasStackTraceContaining("Template must balance independently");
        assertThatThrownBy(()->jdbc.update("insert into gl_transaction_code(id,sacco_id,activity_id,code,name,source_event,created_by,created_at) values(?,'FOREIGN',?,'FOREIGN','Foreign','MANUAL_JOURNAL',?,now())",UUID.randomUUID(),activity,actor.getMemberId())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void listsAndAccountSearchAreBounded() {
        for(int n=0;n<30;n++)service.createActivity(actor,code("ACT"+n,null));
        var first=service.activities(actor,"","",0);assertThat(first.rows()).hasSize(25);assertThat(first.hasNext()).isTrue();
        assertThat(service.activities(actor,"","",1).rows()).hasSize(5);assertThat(service.activities(actor,"act29","",0).rows()).hasSize(1);
        assertThat(service.accounts(actor,"expense",0).rows()).extracting(AccountChoice::code).containsExactly("EXPENSE");
    }
    static CodeForm code(String code,UUID activity) {var form=new CodeForm();form.setCode(code);form.setName(code);form.setActivityId(activity);return form;}
    static TemplateForm template(int revision,String debit,String credit) {var form=new TemplateForm();form.setExpectedRevision(revision);form.setReason("Synthetic template");var pair=new RuleForm();pair.setDebitCode(debit);pair.setCreditCode(credit);form.getRules().add(pair);return form;}
}
