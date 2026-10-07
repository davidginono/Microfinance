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

@EnabledIfEnvironmentVariable(named="MICROFINANCE_LIBRARY_DATABASE_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_library(_single_template)?_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class AccountingCodeLibraryPostgresTest {
    JdbcTemplate jdbc;TransactionTemplate tx;AccountingCodeLibraryService service;GeneralLedgerService ledger;
    AuditService audit;UserClaimService claims;MemberDirectoryService directory;SaccoRegistryService institutions;
    AppUserPrincipal actor;String institution;UUID cash,expense,control,upgradeTransaction,upgradeRequest,upgradeExpense;
    @BeforeAll void database() {
        var ds=new DriverManagerDataSource(System.getenv("MICROFINANCE_LIBRARY_DATABASE_URL"),"microfinance_test","");
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("52").load().migrate();jdbc=new JdbcTemplate(ds);
        assertThat(jdbc.queryForObject("select checksum from flyway_schema_history where version='52' and success",Integer.class)).isEqualTo(1498718278);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").target("53").load().migrate();
        seedPreviousTemplates(ds);
        Flyway.configure().dataSource(ds).locations("classpath:db/migration").load().migrate();
        var manager=new DataSourceTransactionManager(ds);tx=new TransactionTemplate(manager);
        audit=mock(AuditService.class);claims=mock(UserClaimService.class);directory=mock(MemberDirectoryService.class);institutions=mock(SaccoRegistryService.class);
        var clock=mock(ApplicationClock.class);when(clock.now()).thenReturn(OffsetDateTime.now());
        ledger=proxy(new GeneralLedgerService(new GeneralLedgerRepository(jdbc),mock(AccountingPolicyService.class),new AccessControlService(),audit,clock,claims,directory,institutions,mock(com.sacco.mvp.reporting.execution.service.AccountingReleaseGateService.class)),manager);
        service=proxy(new AccountingCodeLibraryService(new AccountingCodeLibraryRepository(jdbc),ledger,audit,clock),manager);
    }
    private void seedPreviousTemplates(DriverManagerDataSource ds) {
        String tenant="UPGRADE-"+UUID.randomUUID();UUID member=UUID.randomUUID(),activity=UUID.randomUUID(),cashAccount=UUID.randomUUID();
        upgradeExpense=UUID.randomUUID();upgradeTransaction=UUID.randomUUID();upgradeRequest=UUID.randomUUID();
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,'Upgrade fixture',true,now(),now())",tenant);
        jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,staff_access_status,created_at,is_member,password_hash) values(?,?,'B1',?,'Upgrade fixture','ACTIVE','ACCOUNTANT','ACTIVE',now(),false,'test-only')",member,tenant,member.toString());
        jdbc.update("insert into gl_account(id,sacco_id,code,name,type,normal_balance,kind,purpose,maker_id,created_at) values(?,?,'CASH','Cash','ASSET','DEBIT','POSTING','CASH',?,now()),(?,?,'EXPENSE','Expense','EXPENSE','DEBIT','POSTING','EXPENSE',?,now())",cashAccount,tenant,member,upgradeExpense,tenant,member);
        jdbc.update("insert into gl_activity(id,sacco_id,code,name,created_by,created_at) values(?,?,'OPS','Operations',?,now())",activity,tenant,member);
        jdbc.update("insert into gl_transaction_code(id,sacco_id,activity_id,code,name,source_event,created_by,created_at) values(?,?,?,'PAY','Payment','MANUAL_JOURNAL',?,now())",upgradeTransaction,tenant,activity,member);
        var transaction=new TransactionTemplate(new DataSourceTransactionManager(ds));
        for(int number=1;number<=2;number++) {
            int sequence=number;
            transaction.executeWithoutResult(status->{
                UUID id=UUID.randomUUID();
                jdbc.update("insert into gl_template_version(id,sacco_id,transaction_id,version,source_event,reason,request_key,payload_hash,created_by,created_at) values(?,?,?,?,'MANUAL_JOURNAL','Legacy fixture',?,'legacy',?,now())",id,tenant,upgradeTransaction,sequence,sequence==2?upgradeRequest:UUID.randomUUID(),member);
                jdbc.update("insert into gl_template_line(template_id,sacco_id,component,side,account_id) values(?,?,'TOTAL','DEBIT',?),(?,?,'TOTAL','CREDIT',?)",id,tenant,sequence==2?cashAccount:upgradeExpense,id,tenant,sequence==2?upgradeExpense:cashAccount);
                jdbc.update("update gl_transaction_code set revision=?,current_template_id=? where id=?",sequence,id,upgradeTransaction);
            });
        }
        jdbc.update("update gl_account set active=false where id=?",upgradeExpense);
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
    @Test void forwardMigrationRemovesHistoryStorage() {
        assertThat(jdbc.queryForObject("select count(*) from flyway_schema_history where version='54' and success",Integer.class)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select to_regclass('gl_template_version') is null and to_regclass('gl_template_line') is null",Boolean.class)).isTrue();
        assertThat(jdbc.queryForObject("select count(*) from information_schema.columns where table_name='gl_transaction_code' and column_name in ('revision','current_template_id')",Integer.class)).isZero();
        assertThat(jdbc.queryForObject("select request_key from gl_transaction_template where transaction_id=?",UUID.class,upgradeTransaction)).isEqualTo(upgradeRequest);
        assertThat(jdbc.queryForObject("select count(*) from gl_transaction_template_line where transaction_id=?",Integer.class,upgradeTransaction)).isEqualTo(2);
        assertThat(jdbc.queryForObject("select account_id from gl_transaction_template_line where transaction_id=? and side='CREDIT'",UUID.class,upgradeTransaction)).isEqualTo(upgradeExpense);
    }
    @Test void transactionOnboardingSavesAccountsAtomicallyAndRollsBackOnFailure() {
        service.createActivity(actor,code("OPS",null));
        var form=onboarding("OPS","PAY","EXPENSE","CASH");UUID id=service.onboardTransaction(actor,form);
        assertThat(service.transaction(actor,id).hasTemplate()).isTrue();assertThat(service.template(actor,id).rules().getFirst().debitCode()).isEqualTo("EXPENSE");
        assertThatThrownBy(()->service.onboardTransaction(actor,onboarding("OPS","FAILED","UNKNOWN","CASH"))).hasMessage("library.error.templateAccount");
        assertThat(service.transactionRegister(actor,null,"FAILED","",0).rows()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from gl_transaction_template where sacco_id=?",Integer.class,institution)).isEqualTo(1);
        assertThatThrownBy(()->service.onboardTransaction(actor,onboarding("FOREIGN","FOREIGN-PAY","EXPENSE","CASH"))).hasMessage("library.error.activityCode");
        assertThatThrownBy(()->service.onboardTransaction(actor,form)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThat(jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,institution)).isZero();
    }
    @Test void transactionRegisterStaysScopedAndBoundedAcrossActivities() {
        UUID first=service.createActivity(actor,code("OPS",null)),second=service.createActivity(actor,code("BANK",null));
        for(int n=0;n<30;n++) {var form=code("PAY"+n,n%2==0?first:second);form.setSourceEvent(n%2==0?"MANUAL_JOURNAL":"EXPENSE");service.createTransaction(actor,form);}
        var page=service.transactionRegister(actor,null,"","",0);assertThat(page.rows()).hasSize(25);assertThat(page.hasNext()).isTrue();
        assertThat(service.transactionRegister(actor,null,"","",1).rows()).hasSize(5);
        assertThat(service.transactionRegister(actor,first,"","",0).rows()).hasSize(15).allMatch(c->c.activityId().equals(first));
        assertThat(service.transactionRegister(actor,null,"","ACTIVE","EXPENSE",0).rows()).hasSize(15).allMatch(c->c.sourceEvent().equals("EXPENSE"));
        assertThat(service.transactionRegister(actor,first,"","ACTIVE","EXPENSE",0).rows()).isEmpty();
        assertThatThrownBy(()->service.transactionRegister(actor,null,"","","UNKNOWN",0)).hasMessage("library.error.sourceEvent");
        assertThatThrownBy(()->service.transactionRegister(actor,UUID.randomUUID(),"","",0)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void editingReplacesTheCurrentAccountsWithoutKeepingCopies() {
        var activity=service.createActivity(actor,code("OPS",null));
        UUID first=service.createTransaction(actor,code("CASH-EXPENSE",activity)),second=service.createTransaction(actor,code("BANK-EXPENSE",activity));
        assertThat(service.transactions(actor,activity,"","",0).rows()).extracting(TransactionCode::id).containsExactlyInAnyOrder(first,second);
        var form=template(null,"EXPENSE","CASH");service.saveTemplate(actor,first,form);
        assertThat(service.saveTemplate(actor,first,form)).isEqualTo(first);
        var next=template(form.getRequestKey(),"CASH","EXPENSE");service.saveTemplate(actor,first,next);
        assertThat(service.template(actor,first).requestKey()).isEqualTo(next.getRequestKey());
        assertThat(service.template(actor,first).rules().getFirst().debitCode()).isEqualTo("CASH");
        assertThat(jdbc.queryForObject("select count(*) from gl_transaction_template where transaction_id=?",Integer.class,first)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from gl_transaction_template_line where transaction_id=?",Integer.class,first)).isEqualTo(2);
        assertThatThrownBy(()->service.saveTemplate(actor,first,form)).hasMessage("library.error.staleTemplate");
        assertThat(jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,institution)).isZero();
        assertThatThrownBy(()->jdbc.update("delete from gl_transaction_template_line where transaction_id=? and side='DEBIT'",first)).hasRootCauseInstanceOf(org.postgresql.util.PSQLException.class);
    }
    @Test void validatesComponentsAccountsAndManualControlBoundary() {
        UUID activity=service.createActivity(actor,code("OPERATIONS",null)),transaction=service.createTransaction(actor,code("PAY",activity));
        var totalAndPrincipal=template(null,"EXPENSE","CASH");var principal=new RuleForm();principal.setComponent("PRINCIPAL");principal.setDebitCode("PRINCIPAL");principal.setCreditCode("CASH");totalAndPrincipal.getRules().add(principal);
        assertThatThrownBy(()->service.saveTemplate(actor,transaction,totalAndPrincipal)).hasMessage("library.error.totalOverlap");
        assertThatThrownBy(()->service.saveTemplate(actor,transaction,template(null,"PRINCIPAL","CASH"))).hasMessage("library.error.manualControl");
        assertThatThrownBy(()->service.saveTemplate(actor,transaction,template(null,"MISSING","CASH"))).hasMessage("library.error.templateAccount");
        ledger.deactivateAccount(actor,expense);
        assertThatThrownBy(()->service.saveTemplate(actor,transaction,template(null,"EXPENSE","CASH"))).hasMessage("library.error.templateAccount");
        ledger.reactivateAccount(actor,expense);
        var specialized=code("REPAY",activity);specialized.setSourceEvent("REPAYMENT");UUID repay=service.createTransaction(actor,specialized);
        var parts=template(null,"PRINCIPAL","CASH");parts.getRules().getFirst().setComponent("PRINCIPAL");service.saveTemplate(actor,repay,parts);
    }
    @Test void scopesAllRecordsAndChecksCurrentClaims() {
        UUID activity=service.createActivity(actor,code("PRIVATE",null)),transaction=service.createTransaction(actor,code("PRIVATE-PAY",activity));
        assertThatThrownBy(()->service.activity(actor,UUID.randomUUID())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->service.transaction(actor,UUID.randomUUID())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->service.template(actor,UUID.randomUUID())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        String foreign="FOREIGN-"+UUID.randomUUID();UUID foreignId=UUID.randomUUID();
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,'Synthetic foreign',true,now(),now())",foreign);
        jdbc.update("insert into gl_activity(id,sacco_id,code,name,created_by,created_at) values(?,?,'FOREIGN','Foreign',?,now())",foreignId,foreign,actor.getMemberId());
        assertThatThrownBy(()->service.activity(actor,foreignId)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->service.createTransaction(actor,code("FOREIGN-PAY",foreignId))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        when(claims.effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(Set.of());
        assertThatThrownBy(()->service.createActivity(actor,code("REVOKED",null))).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        assertThatThrownBy(()->service.activities(actor,"","",0)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void lifecycleProtectsParentsAndKeepsConfiguration() {
        UUID activity=service.createActivity(actor,code("OPS",null)),transaction=service.createTransaction(actor,code("PAY",activity));
        UUID version=service.saveTemplate(actor,transaction,template(null,"EXPENSE","CASH"));
        assertThatThrownBy(()->service.activityState(actor,activity,false)).hasMessage("library.error.activeTransactions");
        service.transactionState(actor,transaction,false);service.activityState(actor,activity,false);
        assertThatThrownBy(()->service.transactionState(actor,transaction,true)).hasMessage("library.error.inactiveActivity");
        assertThat(service.template(actor,transaction)).isNotNull();
        service.activityState(actor,activity,true);service.transactionState(actor,transaction,true);
        assertThatThrownBy(()->jdbc.update("delete from gl_transaction_code where id=?",transaction)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void auditFailureRollsBackAndDuplicateCodesStayUnique() {
        UUID activity=service.createActivity(actor,code("OPS",null)),transaction=service.createTransaction(actor,code("PAY",activity));
        doThrow(new IllegalStateException("Audit failed")).when(audit).logEvent(anyString(),any(),anyString(),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),anyMap());
        assertThatThrownBy(()->service.saveTemplate(actor,transaction,template(null,"EXPENSE","CASH"))).isInstanceOf(IllegalStateException.class);
        assertThat(service.transaction(actor,transaction).hasTemplate()).isFalse();assertThat(service.template(actor,transaction)).isNull();reset(audit);
        assertThatThrownBy(()->service.createActivity(actor,code("ops",null))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->service.createTransaction(actor,code("PAY",activity))).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void concurrentRetriesSaveOnceAndStaleWritesCannotReplaceConfiguration() throws Exception {
        UUID activity=service.createActivity(actor,code("OPS",null)),transaction=service.createTransaction(actor,code("PAY",activity));
        UUID key=UUID.randomUUID();
        try(var pool=Executors.newFixedThreadPool(2)) {
            var a=template(null,"EXPENSE","CASH");a.setRequestKey(key);var b=template(null,"EXPENSE","CASH");b.setRequestKey(key);
            var start=new CountDownLatch(1);var first=pool.submit(()->{start.await();return service.saveTemplate(actor,transaction,a);});var second=pool.submit(()->{start.await();return service.saveTemplate(actor,transaction,b);});start.countDown();
            assertThat(first.get(20,TimeUnit.SECONDS)).isEqualTo(second.get(20,TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("select count(*) from gl_transaction_template where transaction_id=?",Integer.class,transaction)).isEqualTo(1);
        assertThatThrownBy(()->service.saveTemplate(actor,transaction,template(null,"CASH","EXPENSE"))).hasMessage("library.error.staleTemplate");
        var changed=template(null,"CASH","EXPENSE");changed.setRequestKey(key);
        assertThatThrownBy(()->service.saveTemplate(actor,transaction,changed)).hasMessage("library.error.changedRetry");
    }
    @Test void databaseRejectsUnbalancedTemplatesAndForeignOwnership() {
        UUID activity=service.createActivity(actor,code("OPS",null)),transaction=service.createTransaction(actor,code("PAY",activity));
        assertThatThrownBy(()->tx.executeWithoutResult(status->{
            jdbc.update("insert into gl_transaction_template(transaction_id,sacco_id,request_key,payload_hash,saved_by) values(?,?,?,'hash',?)",transaction,institution,UUID.randomUUID(),actor.getMemberId());
            jdbc.update("insert into gl_transaction_template_line(transaction_id,sacco_id,component,side,account_id) values(?,?,'TOTAL','DEBIT',?)",transaction,institution,expense);
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
    static TransactionForm onboarding(String activity,String code,String debit,String credit) {var form=new TransactionForm();form.setActivityCode(activity);form.setCode(code);form.setName(code);form.setTemplate(template(null,debit,credit));return form;}
    static TemplateForm template(UUID expected,String debit,String credit) {var form=new TemplateForm();form.setExpectedRequestKey(expected);var pair=new RuleForm();pair.setDebitCode(debit);pair.setCreditCode(credit);form.getRules().add(pair);return form;}
}
