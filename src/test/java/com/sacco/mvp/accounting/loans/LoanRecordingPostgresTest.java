package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.loans.repository.LoanRecordingRepository;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.business.repository.BusinessAccountingRepository;
import com.sacco.mvp.accounting.business.service.BusinessAccountingGuard;
import com.sacco.mvp.accounting.repository.*;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.accounting.policy.AccountingPolicyService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.jdbc.core.JdbcTemplate;
import com.zaxxer.hikari.HikariDataSource;
import org.springframework.orm.jpa.*;
import org.springframework.orm.jpa.vendor.HibernateJpaVendorAdapter;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.json.JsonMapper;
import javax.sql.DataSource;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoanRecordingPostgresTest {
    static final LocalDate TODAY=LocalDate.of(2026,10,8);
    AnnotationConfigApplicationContext context;JdbcTemplate jdbc;LoanRecordingService service;LoanRepaymentLedgerService ledger;
    String institution;AppUserPrincipal actor;UUID client,product,cash,principal,interest;
    @BeforeAll void start(){context=new AnnotationConfigApplicationContext(Config.class);jdbc=context.getBean(JdbcTemplate.class);service=context.getBean(LoanRecordingService.class);ledger=context.getBean(LoanRepaymentLedgerService.class);}
    @AfterAll void stop(){if(context!=null)context.close();}
    @BeforeEach void fixture(){
        reset(context.getBean(AuditService.class),context.getBean(MemberDirectoryService.class),context.getBean(UserClaimService.class),context.getBean(SaccoRegistryService.class));
        institution="LR-"+UUID.randomUUID();jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,'Synthetic loan institution',true,now(),now())",institution);
        for(String branch:List.of("B1","B2"))jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,access_status,created_at,updated_at) values(?,?,?,true,'ACTIVE',now(),now())",UUID.randomUUID(),institution,branch);
        actor=operator("B1");client=UUID.randomUUID();jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,is_member,password_hash,created_at) values(?,?, 'B1',?,'Synthetic client','ACTIVE','MEMBER',true,'test-only',now())",client,institution,client.toString());
        product=UUID.randomUUID();jdbc.update("insert into loan_product_settings(id,sacco_id,loan_type,active,product_status,product_code,product_name,form_schema,guarantors_required,interest_rate,interest_method,repayment_frequency,min_repayment_months,max_repayment_months,created_at,updated_at) values(?,?,'DEVELOPMENT_LOAN',true,'ACTIVE','BUSINESS','Synthetic business loan','{}',0,0.2400,'FLAT_RATE','MONTHLY',1,24,now(),now())",product,institution);
        cash=account("CASH","ASSET","DEBIT","POSTING","CASH");principal=account("LOANS","ASSET","DEBIT","CONTROL","LOAN_PRINCIPAL");interest=account("INTEREST","LIABILITY","CREDIT","POSTING","CLEARING");
        var institutions=context.getBean(SaccoRegistryService.class);when(institutions.findActiveSacco(institution)).thenReturn(Optional.of(RegisteredSacco.builder().saccoId(institution).active(true).build()));when(institutions.findStation(eq(institution),anyString())).thenAnswer(i->Optional.of(SaccoStation.builder().active(true).accessStatus(SaccoAccessStatus.ACTIVE).saccoId(institution).stationId(i.getArgument(1)).build()));
        when(context.getBean(UserClaimService.class).effectiveClaims(any(),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));
    }
    @Test void recordingDoesNotMoveMoneyAndFrozenProductTermsSurviveProductChanges(){
        var f=form();var id=service.save(actor,f);assertThat(service.save(actor,f)).isEqualTo(id);assertThat(service.detail(actor,id).loan().outstandingPrincipal()).isZero();
        assertThat(jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,institution)).isZero();
        jdbc.update("update loan_product_settings set interest_rate=0.5000 where id=?",product);
        var disbursement=service.disburse(actor,post(id,"1000.00","D1"));assertThat(service.detail(actor,id).contractualInterest()).isEqualByComparingTo("20.00");assertThat(disbursement.principalBalance()).isEqualByComparingTo("1000.00");
        assertThatThrownBy(()->jdbc.update("update loan_applications set amount=1 where id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForMap("select direct_post,checker_id,policy_id,state from gl_journal where id=?",disbursement.journalId())).containsEntry("direct_post",true).containsEntry("checker_id",null).containsEntry("policy_id",null).containsEntry("state","POSTED");
    }
    @Test void directPaymentRetryIndependentReversalAndClientBalanceAgree(){
        var id=service.save(actor,form());service.disburse(actor,post(id,"1000.00","D1"));var f=post(id,"120.00","R1");f.setPrincipalAccountId(null);f.setInterestAccountId(interest);
        var paid=service.repay(actor,f);assertThat(paid.principal()).isEqualByComparingTo("100.00");assertThat(paid.interest()).isEqualByComparingTo("20.00");assertThat(paid.principalBalance()).isEqualByComparingTo("900.00");
        assertThat(service.repay(actor,f).id()).isEqualTo(paid.id());assertThat(service.statement(actor,client,0).principal()).isEqualByComparingTo("900.00");
        assertThatThrownBy(()->service.reverse(actor,paid.id(),UUID.randomUUID(),TODAY,"Wrong collection")).hasMessage("recording.error.independentCorrection");
        assertThatThrownBy(()->jdbc.update("""
            insert into accountant_loan_post(id,loan_id,sacco_id,station_id,kind,state,request_key,payload_hash,
            effective_date,reference,amount,money_account_id,principal_account_id,interest_account_id,
            money_account_name,principal_account_name,interest_account_name,evidence,notes,actor_id,recorded_at,journal_id,reverses_id)
            select gen_random_uuid(),loan_id,sacco_id,station_id,'REVERSAL','POSTING',gen_random_uuid(),payload_hash,
            effective_date,reference,amount,money_account_id,principal_account_id,interest_account_id,
            money_account_name,principal_account_name,interest_account_name,evidence,notes,actor_id,recorded_at,journal_id,id
            from accountant_loan_post where id=?
            """,paid.id())).isInstanceOf(org.springframework.dao.DataAccessException.class).hasStackTraceContaining("A different accountant must record the repayment correction");
        var corrector=operator("B1");var key=UUID.randomUUID();var reversed=service.reverse(corrector,paid.id(),key,TODAY,"Wrong collection");assertThat(reversed.principalBalance()).isEqualByComparingTo("1000.00");assertThat(service.reverse(corrector,paid.id(),key,TODAY,"Wrong collection").id()).isEqualTo(reversed.id());
        assertThat(service.posting(actor,paid.id()).reversedBy()).isEqualTo(reversed.id());assertThatThrownBy(()->service.reverse(corrector,paid.id(),UUID.randomUUID(),TODAY,"Again")).hasMessage("recording.error.reversal");
        var filter=new Filter("R1",TODAY,TODAY,"","newest",0,null);
        assertThat(service.report(actor,"PAYMENT",filter)).hasSize(2);
        var totals=service.totals(actor,"PAYMENT",filter);
        assertThat(totals.count()).isEqualTo(2);assertThat(totals.amount()).isZero();assertThat(totals.principal()).isZero();assertThat(totals.interest()).isZero();
        assertThat(jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where sacco_id=?",BigDecimal.class,institution)).isZero();
        assertThat(jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where sacco_id=? and account_id=?",BigDecimal.class,institution,principal)).isEqualByComparingTo("1000.00");
        var owner=new AppUserPrincipal(Member.builder().id(client).saccoId(institution).stationId("B1").memberAccount(true).status(MemberStatus.ACTIVE).build(),Set.of(UserClaim.MEMBER_LOANS_VIEW),false);
        assertThat(ledger.view(id,owner,0,0).outstandingPrincipal()).isEqualByComparingTo("1000.00");assertThat(ledger.view(id,owner,0,0).journal()).isEmpty();
    }
    @Test void rejectedAdvanceAndAuditFailureLeaveNoPartialFinancialRecords(){
        var id=service.save(actor,form());service.disburse(actor,post(id,"1000.00","D1"));var f=post(id,"1020.01","OVER");f.setPrincipalAccountId(null);f.setInterestAccountId(interest);
        assertThatThrownBy(()->service.repay(actor,f)).hasMessage("repayment.error.advance");
        f.setAmount(new BigDecimal("120.00"));f.setReference("R1");
        doThrow(new IllegalStateException("Audit unavailable")).when(context.getBean(AuditService.class)).logEvent(anyString(),any(),anyString(),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),anyMap());
        assertThatThrownBy(()->service.repay(actor,f)).hasMessage("Audit unavailable");assertThat(service.detail(actor,id).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
        assertThat(jdbc.queryForObject("select count(*) from loan_repayment_transactions where loan_application_id=?",Integer.class,id)).isZero();assertThat(jdbc.queryForObject("select count(*) from accountant_loan_post where loan_id=?",Integer.class,id)).isEqualTo(1);
    }
    @Test void scopeRevocationClosedPeriodAndChangedRetryAreRejected(){
        var f=form();var id=service.save(actor,f);var foreign=operator("B2");assertThatThrownBy(()->service.detail(foreign,id)).isInstanceOf(AccessDeniedException.class);
        f.setPrincipal(new BigDecimal("2000.00"));assertThatThrownBy(()->service.save(actor,f)).hasMessage("recording.error.retry");
        jdbc.update("insert into accounting_period(id,sacco_id,starts_on,ends_on,state,created_by,created_at) values(?,?,?,?,'CLOSED',?,now())",UUID.randomUUID(),institution,LocalDate.of(2026,9,1),LocalDate.of(2026,9,1),actor.getMemberId());
        assertThatThrownBy(()->service.disburse(actor,post(id,"1000.00","D1"))).hasMessage("voucher.error.period");
        when(context.getBean(UserClaimService.class).effectiveClaims(eq(actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of());assertThatThrownBy(()->service.detail(actor,id)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void concurrentPaymentRetriesPostOnceAndSettlementIsBalanceBased()throws Exception{
        var id=service.save(actor,form());service.disburse(actor,post(id,"1000.00","D1"));var f=post(id,"1020.00","R1");f.setPrincipalAccountId(null);f.setInterestAccountId(interest);
        var pool=Executors.newFixedThreadPool(2);try{var first=pool.submit(()->service.repay(actor,f));var second=pool.submit(()->service.repay(actor,f));assertThat(first.get(30,TimeUnit.SECONDS).id()).isEqualTo(second.get(30,TimeUnit.SECONDS).id());}finally{pool.shutdownNow();}
        assertThat(service.detail(actor,id).loan().status()).isEqualTo("PAID");assertThat(service.detail(actor,id).loan().outstandingPrincipal()).isZero();assertThat(jdbc.queryForObject("select count(*) from loan_repayment_transactions where loan_application_id=?",Integer.class,id)).isEqualTo(1);
    }
    @Test void ordinaryRepaymentRouteCannotBypassDirectGlAndReferenceUniqueness(){
        var id=service.save(actor,form());service.disburse(actor,post(id,"1000.00","D1"));
        assertThatThrownBy(()->ledger.post(id,actor,new LoanRepaymentLedgerService.PaymentCommand(new BigDecimal("120.00"),TODAY,LoanRepaymentTransaction.Channel.CASH,"R1",UUID.randomUUID()))).hasMessage("recording.error.directPath");
        var f=post(id,"120.00","R1");f.setPrincipalAccountId(null);f.setInterestAccountId(interest);service.repay(actor,f);f.setRequestKey(UUID.randomUUID());assertThatThrownBy(()->service.repay(actor,f)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
    }
    @Test void previewsDoNotPostAndCannotSilentlyIgnoreProductCharges(){
        assertThat(service.preview(actor,form()).interest()).isEqualByComparingTo("20.00");
        assertThat(jdbc.queryForObject("select count(*) from accountant_loan_record where sacco_id=?",Integer.class,institution)).isZero();
        jdbc.update("update loan_product_settings set application_fee=10 where id=?",product);
        assertThatThrownBy(()->service.save(actor,form())).hasMessage("recording.error.productFees");
        jdbc.update("update loan_product_settings set application_fee=0 where id=?",product);
        var id=service.save(actor,form());service.disburse(actor,post(id,"1000.00","D1"));
        var f=post(id,"120.00","R1");f.setInterestAccountId(interest);
        var preview=service.postingPreview(actor,f,"PAYMENT");
        assertThat(preview.principal()).isEqualByComparingTo("100.00");
        assertThat(preview.interest()).isEqualByComparingTo("20.00");
        assertThat(preview.principalBalance()).isEqualByComparingTo("900.00");
        assertThat(jdbc.queryForObject("select count(*) from loan_repayment_transactions where loan_application_id=?",Integer.class,id)).isZero();
    }
    @Test void staleAccountantSessionCannotKeepRecordingAfterCurrentRoleRemoval(){
        var current=context.getBean(MemberDirectoryService.class).find(actor.getMemberId()).orElseThrow();
        current.setPosition(Position.MANAGER);
        assertThatThrownBy(()->service.save(actor,form())).isInstanceOf(AccessDeniedException.class);
    }
    @Test void historicalBranchRetainsItsLedgerWhenClientMoves(){
        var id=service.save(actor,form());service.disburse(actor,post(id,"1000.00","D1"));
        jdbc.update("update members set station_id='B2',status='INACTIVE' where id=?",client);
        assertThat(service.statement(actor,client,0).principal()).isEqualByComparingTo("1000.00");
        assertThat(service.exportStatement(actor,client).loans()).hasSize(1);
        assertThat(service.choices(actor,"ledger-clients","",0)).extracting(Choice::id).contains(client);
        assertThatThrownBy(()->service.save(actor,form())).hasMessage("recording.error.client");
    }
    @Test void fractionalPrincipalSettlesExactlyAfterMultipleRoundedInstalments(){
        var f=form();f.setPrincipal(new BigDecimal("1000.01"));f.setRequestedPrincipal(new BigDecimal("1000.01"));
        f.setMonths(3);f.setApplicationDate(LocalDate.of(2026,7,1));f.setFirstPaymentDate(LocalDate.of(2026,8,1));
        var id=service.save(actor,f);var disb=post(id,"1000.01","D1");disb.setEffectiveDate(LocalDate.of(2026,7,1));service.disburse(actor,disb);
        var payment=post(id,"1060.01","R1");payment.setInterestAccountId(interest);payment.setPrincipalAccountId(null);
        var posted=service.repay(actor,payment);
        assertThat(posted.principal()).isEqualByComparingTo("1000.01");assertThat(posted.interest()).isEqualByComparingTo("60.00");
        assertThat(posted.principalBalance()).isZero();assertThat(service.detail(actor,id).loan().status()).isEqualTo("PAID");
    }
    RecordForm form(){var f=new RecordForm();f.setClientId(client);f.setProductId(product);f.setApplicationDate(LocalDate.of(2026,9,1));f.setFirstPaymentDate(LocalDate.of(2026,10,1));f.setMonths(1);f.setRequestedPrincipal(new BigDecimal("1000.00"));f.setPrincipal(new BigDecimal("1000.00"));f.setPurpose("Business stock");return f;}
    PostForm post(UUID loan,String amount,String reference){var f=new PostForm();f.setLoanId(loan);f.setAmount(new BigDecimal(amount));f.setEffectiveDate(reference.startsWith("D")?LocalDate.of(2026,9,1):TODAY);f.setMoneyAccountId(cash);f.setPrincipalAccountId(principal);f.setReference(reference);return f;}
    UUID account(String code,String type,String normal,String kind,String purpose){var id=UUID.randomUUID();jdbc.update("insert into gl_account(id,sacco_id,code,name,type,normal_balance,kind,purpose,maker_id,created_at) values(?,?,?,?,?,?,?,?,?,now())",id,institution,code,code,type,normal,kind,purpose,actor.getMemberId());return id;}
    AppUserPrincipal operator(String branch){var id=UUID.randomUUID();jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,is_member,staff_access_status,password_hash,created_at) values(?,?,?,?, 'Synthetic accountant','ACTIVE','ACCOUNTANT',false,'ACTIVE','test-only',now())",id,institution,branch,id.toString());var member=Member.builder().id(id).saccoId(institution).stationId(branch).memberNo(id.toString()).fullName("Synthetic accountant").status(MemberStatus.ACTIVE).position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).build();when(context.getBean(MemberDirectoryService.class).find(id)).thenReturn(Optional.of(member));return new AppUserPrincipal(member,EnumSet.allOf(UserClaim.class),true);}
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement @EnableJpaRepositories(basePackages="com.sacco.mvp.repository")
    static class Config {
        @Bean(destroyMethod="close") DataSource dataSource(){var ds=new HikariDataSource();ds.setJdbcUrl(System.getenv("MICROFINANCE_RECORDING_TEST_URL"));ds.setUsername("microfinance_test");ds.setPassword("");ds.addDataSourceProperty("sslmode","disable");ds.setMinimumIdle(1);ds.setMaximumPoolSize(4);ds.setConnectionTimeout(60000);return ds;}
        @Bean(initMethod="migrate") Flyway flyway(DataSource ds){return Flyway.configure().dataSource(ds).locations("classpath:db/migration").load();}
        @Bean @DependsOn("flyway") LocalContainerEntityManagerFactoryBean entityManagerFactory(DataSource ds){var f=new LocalContainerEntityManagerFactoryBean();f.setDataSource(ds);f.setPackagesToScan("com.sacco.mvp.domain");f.setJpaVendorAdapter(new HibernateJpaVendorAdapter());f.setJpaPropertyMap(Map.of("hibernate.hbm2ddl.auto","validate","hibernate.physical_naming_strategy","org.hibernate.boot.model.naming.CamelCaseToUnderscoresNamingStrategy"));return f;}
        @Bean PlatformTransactionManager transactionManager(jakarta.persistence.EntityManagerFactory f){return new JpaTransactionManager(f);}
        @Bean JdbcTemplate jdbc(DataSource ds){return new JdbcTemplate(ds);}
        @Bean ObjectMapper json(){return JsonMapper.builder().findAndAddModules().build();}
        @Bean ApplicationClock clock(){var c=mock(ApplicationClock.class);when(c.today()).thenReturn(TODAY);when(c.now()).thenReturn(OffsetDateTime.parse("2026-10-08T12:00:00+03:00"));return c;}
        @Bean AuditService audit(){return mock(AuditService.class);}
        @Bean MemberDirectoryService directory(){return mock(MemberDirectoryService.class);}
        @Bean UserClaimService claims(){return mock(UserClaimService.class);}
        @Bean SaccoRegistryService institutions(){return mock(SaccoRegistryService.class);}
        @Bean GeneralLedgerService accounting(JdbcTemplate j,AuditService audit,ApplicationClock clock,UserClaimService claims,MemberDirectoryService directory,SaccoRegistryService institutions){return new GeneralLedgerService(new GeneralLedgerRepository(j),mock(AccountingPolicyService.class),new AccessControlService(),audit,clock,claims,directory,institutions,mock(com.sacco.mvp.reporting.execution.service.AccountingReleaseGateService.class));}
        @Bean BusinessAccountingGuard guard(JdbcTemplate j,ObjectMapper mapper){return new BusinessAccountingGuard(new BusinessAccountingRepository(j,mapper));}
        @Bean LoanRepaymentLedgerService repayments(LoanLedgerRepository ledgers,LoanLedgerInstallmentRepository installments,LoanRepaymentTransactionRepository transactions,LoanRepaymentAllocationRepository allocations,LoanJournalEntryRepository journal,LoanApplicationRepository loans,ObjectMapper mapper,ApplicationClock clock,AuditService audit,BusinessAccountingGuard guard){return new LoanRepaymentLedgerService(ledgers,installments,transactions,allocations,journal,loans,mapper,clock,new AccessControlService(),audit,guard);}
        @Bean LoanRecordingService recording(JdbcTemplate j,LoanApplicationRepository loans,LoanProductSettingRepository products,LoanRepaymentLedgerService repayments,GeneralLedgerService accounting,ObjectMapper mapper,ApplicationClock clock,AuditService audit){var numbers=mock(ApplicationNumberService.class);var next=new AtomicLong(1000000000);when(numbers.nextFor(anyString())).thenAnswer(i->next.incrementAndGet());return new LoanRecordingService(new LoanRecordingRepository(j),loans,products,numbers,new RepaymentScheduleService(mapper,products),repayments,accounting,new VoucherRepository(j),mapper,clock,audit);}
    }
}
