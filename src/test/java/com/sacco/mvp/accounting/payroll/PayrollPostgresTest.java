package com.sacco.mvp.accounting.payroll;

import com.sacco.mvp.accounting.payroll.dto.PayrollDtos.*;
import com.sacco.mvp.accounting.payroll.repository.PayrollRepository;
import com.sacco.mvp.accounting.payroll.service.PayrollService;
import com.sacco.mvp.accounting.payroll.service.PayrollSpreadsheetService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import com.zaxxer.hikari.HikariDataSource;
import org.flywaydb.core.Flyway;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.JdbcTransactionManager;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.transaction.annotation.EnableTransactionManagement;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicLong;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_PAYROLL_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_payroll_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PayrollPostgresTest {
    static final LocalDate TODAY=LocalDate.of(2026,10,19);
    static final AtomicLong NUMBERS=new AtomicLong(System.currentTimeMillis()*1000);
    AnnotationConfigApplicationContext context;JdbcTemplate jdbc;PayrollService service;
    String institution;AppUserPrincipal actor;UUID client,loan,employer;
    @BeforeAll void start(){context=new AnnotationConfigApplicationContext(Config.class);jdbc=context.getBean(JdbcTemplate.class);service=context.getBean(PayrollService.class);}
    @AfterAll void close(){if(context!=null)context.close();}
    @BeforeEach void fixture(){
        reset(context.getBean(AuditService.class));
        institution="PAY-"+UUID.randomUUID();
        jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,'Synthetic payroll institution',true,now(),now())",institution);
        for(var b:List.of("B1","B2"))jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,access_status,created_at,updated_at) values(?,?,?,true,'ACTIVE',now(),now())",UUID.randomUUID(),institution,b);
        actor=actor(institution,"B1");client=member("B1","EMP-001");loan=loan(client,"B1");
        var f=new EmployerForm();f.setName("Synthetic Employer");f.setAgreementReference("Synthetic employer agreement");employer=service.createEmployer(actor,f);
    }
    @Test void capsUnpaidInstalmentsAndKeepsArrearsOptionalWithoutPostingMoney(){
        instruction(loan,"150.01");var f=batch();var p=service.preview(actor,f);
        assertThat(p.total()).isEqualByComparingTo("98.00");assertThat(p.rows().getFirst().principal()).isEqualByComparingTo("90.00");assertThat(p.rows().getFirst().interest()).isEqualByComparingTo("8.00");
        f.setIncludeArrears(true);p=service.preview(actor,f);assertThat(p.total()).isEqualByComparingTo("150.01");
        f.setPreviewHash(p.hash());var id=service.freeze(actor,f);assertThat(service.freeze(actor,f)).isEqualTo(id);
        assertThat(service.batch(actor,id).total()).isEqualByComparingTo("150.01");
        assertThat(jdbc.queryForObject("select count(*) from loan_repayment_transactions where loan_application_id=?",Long.class,loan)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Long.class,institution)).isZero();
        assertThat(jdbc.queryForObject("select principal_paid from loan_ledger_installments where loan_application_id=? and installment_number=2",BigDecimal.class,loan)).isEqualByComparingTo("10.00");
        assertThatThrownBy(()->service.freeze(actor,batch())).hasMessage("payroll.error.batchExists");
        f.setIncludeArrears(false);assertThatThrownBy(()->service.freeze(actor,f)).hasMessage("payroll.error.retry");
    }
    @Test void profileChangeBecomesAnExplicitExceptionAndOldInstructionsMustBeStopped(){
        UUID instruction=instruction(loan,"200.00");var other=member("B1","EMP-002");instruction(loan(other,"B1"),"200.00");
        jdbc.update("update members set employee_number='EMP-CHANGED' where id=?",client);
        var f=batch();var p=service.preview(actor,f);assertThat(p.exceptions()).isEqualTo(1);assertThat(p.rows()).anyMatch(r->r.issue().equals("employeeChanged") && r.amount().signum()==0);
        f.setPreviewHash(p.hash());assertThatThrownBy(()->service.freeze(actor,f)).hasMessage("payroll.error.exceptions");
        f.setAcceptExceptions(true);var id=service.freeze(actor,f);assertThat(service.batch(actor,id).employees()).isEqualTo(1);assertThat(service.lines(actor,id,0).rows()).hasSize(2);
        assertThatThrownBy(()->instruction(loan,"100.00")).hasMessage("payroll.error.instructionExists");
        service.stop(actor,instruction,"Synthetic employee mapping renewal");assertThat(instruction(loan,"100.00")).isNotNull();
    }
    @Test void rejectsStalePreviewsAndImmutableSnapshotsSurviveSubsequentChanges(){
        instruction(loan,"200.00");var f=batch();f.setPreviewHash(service.preview(actor,f).hash());
        instruction(loan(member("B1","EMP-002"),"B1"),"200.00");
        assertThatThrownBy(()->service.freeze(actor,f)).hasMessage("payroll.error.stale");
        f.setPreviewHash(service.preview(actor,f).hash());var id=service.freeze(actor,f);
        var before=service.lines(actor,id,0).rows();jdbc.update("update members set employee_number='CHANGED',full_name='Changed profile name' where id=?",client);
        assertThat(service.lines(actor,id,0).rows()).isEqualTo(before);
        assertThatThrownBy(()->jdbc.update("update payroll_batch_line set amount=1 where batch_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("delete from payroll_batch where id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void preventsBranchTenantAndEmployeeCollisionsAndRechecksClaims(){
        assertThatThrownBy(()->instruction(loan(member("B2","EMP-B2"),"B2"),"100.00")).hasMessage("payroll.error.loan");
        instruction(loan,"100.00");var duplicate=loan(member("B1","EMP-001"),"B1");assertThatThrownBy(()->instruction(duplicate,"100.00")).hasMessage("payroll.error.instructionExists");
        assertThatThrownBy(()->service.employer(actor(institution,"B2"),employer)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.employer(actor("OTHER-TENANT","B1"),employer)).isInstanceOf(AccessDeniedException.class);
        doThrow(new AccessDeniedException("Revoked")).when(context.getBean(GeneralLedgerService.class)).requireActor(actor,"PAYROLL_DEDUCTIONS_EXPORT");
        assertThatThrownBy(()->service.exportLines(actor,UUID.randomUUID())).isInstanceOf(AccessDeniedException.class);
        reset(context.getBean(GeneralLedgerService.class));
        for(var role:Position.values())assertThat(UserClaim.defaultClaims(Set.of(role),false)).doesNotContain(UserClaim.PAYROLL_DEDUCTIONS_VIEW,UserClaim.PAYROLL_DEDUCTIONS_CREATE,UserClaim.PAYROLL_DEDUCTIONS_UPDATE,UserClaim.PAYROLL_DEDUCTIONS_EXPORT);
    }
    @Test void retainsPdfBytesAndDownloadAuditAndRequiresWithdrawalBeforeReplacingSentBatch(){
        instruction(loan,"100.00");var f=batch();f.setPreviewHash(service.preview(actor,f).hash());var id=service.freeze(actor,f);
        var document=new Document(new byte[]{1,2,3},PayrollService.hash(new byte[]{1,2,3}));service.downloaded(actor,id,"en",document);
        assertThat(service.downloaded(actor,id,"en",new Document(new byte[]{4},PayrollService.hash(new byte[]{4}))).content()).containsExactly(1,2,3);
        assertThat(service.events(actor,id,0).rows()).hasSize(2);
        service.changeState(actor,id,"SENT","Synthetic employer delivery receipt",false);
        assertThatThrownBy(()->service.changeState(actor,id,"CANCELLED","Synthetic withdrawal",false)).hasMessage("payroll.error.withdrawal");
        service.changeState(actor,id,"CANCELLED","Synthetic employer withdrawal reference",true);
        assertThatThrownBy(()->service.freeze(actor,batch())).hasMessage("payroll.error.replacement");
        var replacement=batch();replacement.setReplacesId(id);replacement.setPreviewHash(service.preview(actor,replacement).hash());var next=service.freeze(actor,replacement);
        assertThat(service.batch(actor,next).replacesId()).isEqualTo(id);assertThat(service.batch(actor,id).state()).isEqualTo("CANCELLED");
        assertThatThrownBy(()->service.changeState(actor,id,"SENT","Synthetic attempt",false)).hasMessage("payroll.error.state");
    }
    @Test void spreadsheetExportUsesFrozenIdentityAndAmountsAuditsDownloadsAndNeverPosts()throws Exception {
        jdbc.update("update members set employee_number='000007' where id=?",client);
        instruction(loan,"200.00");var f=batch();f.setPreviewHash(service.preview(actor,f).hash());var id=service.freeze(actor,f);
        var frozen=service.exportLines(actor,id);
        jdbc.update("update members set employee_number='CHANGED',full_name='Changed name' where id=?",client);
        jdbc.update("update loan_ledger_installments set principal_paid=100,interest_paid=10 where loan_application_id=?",loan);
        assertThat(service.exportLines(actor,id)).isEqualTo(frozen);
        var messages=new org.springframework.context.support.ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");
        var renderer=new PayrollSpreadsheetService(messages,new ReportExportLimiter(1));
        var bytes=renderer.render(service.batch(actor,id),service.exportLines(actor,id),Locale.ENGLISH);
        var document=service.spreadsheetDownloaded(actor,id,"en",bytes);
        assertThat(document.content()).containsExactly(bytes);assertThat(document.checksum()).isEqualTo(PayrollService.hash(bytes));
        try(var book=new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(document.content()))) {
            var sheet=book.getSheetAt(0);var row=sheet.getRow(sheet.getPaneInformation().getHorizontalSplitPosition());
            assertThat(row.getCell(0).getStringCellValue()).isEqualTo("000007");assertThat(row.getCell(1).getStringCellValue()).isEqualTo("Synthetic Payroll Employee");
            assertThat(row.getCell(3).getNumericCellValue()).isEqualTo(98.00);
        }
        assertThat(service.events(actor,id,0).rows()).singleElement().satisfies(e->{assertThat(e.kind()).isEqualTo("DOWNLOAD");assertThat(e.evidence()).isEqualTo("XLSX / en / SHA-256 "+document.checksum());});
        assertThat(service.document(actor,id,"en")).isEmpty();assertThat(service.batch(actor,id).state()).isEqualTo("FROZEN");
        for(var foreign:List.of(actor(institution,"B2"),actor("OTHER-TENANT","B1"))) {
            assertThatThrownBy(()->service.exportLines(foreign,id)).isInstanceOf(AccessDeniedException.class);
            assertThatThrownBy(()->service.spreadsheetDownloaded(foreign,id,"en",bytes)).isInstanceOf(AccessDeniedException.class);
        }
        doThrow(new AccessDeniedException("Revoked")).when(context.getBean(GeneralLedgerService.class)).requireActor(actor,"PAYROLL_DEDUCTIONS_EXPORT");
        try {assertThatThrownBy(()->service.spreadsheetDownloaded(actor,id,"en",bytes)).isInstanceOf(AccessDeniedException.class);}
        finally {reset(context.getBean(GeneralLedgerService.class));}
        assertThat(service.events(actor,id,0).rows()).hasSize(1);
        assertThat(jdbc.queryForObject("select count(*) from loan_repayment_transactions where loan_application_id=?",Long.class,loan)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Long.class,institution)).isZero();
        assertThat(jdbc.queryForObject("select sum(principal_paid) from loan_ledger_installments where loan_application_id=?",BigDecimal.class,loan)).isEqualByComparingTo("300.00");
        assertThat(jdbc.queryForObject("select sum(interest_paid) from loan_ledger_installments where loan_application_id=?",BigDecimal.class,loan)).isEqualByComparingTo("30.00");
    }
    @Test void concurrentFreezeHasOneWinnerAndAuditFailureRollsBackEverything()throws Exception {
        instruction(loan,"100.00");var f=batch();f.setPreviewHash(service.preview(actor,f).hash());
        var ready=new CountDownLatch(2);var go=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            Callable<UUID> work=()->{ready.countDown();go.await();return service.freeze(actor,f);};
            var first=pool.submit(work);var second=pool.submit(work);assertThat(ready.await(10,TimeUnit.SECONDS)).isTrue();go.countDown();assertThat(first.get(15,TimeUnit.SECONDS)).isEqualTo(second.get(15,TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("select count(*) from payroll_batch where sacco_id=?",Long.class,institution)).isEqualTo(1);
        var another=batch();another.setMonth("2026-11");another.setDeductionDate(LocalDate.of(2026,11,30));another.setPreviewHash(service.preview(actor,another).hash());
        doThrow(new IllegalStateException("Synthetic audit failure")).when(context.getBean(AuditService.class)).logEvent(anyString(),any(),anyString(),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),any());
        assertThatThrownBy(()->service.freeze(actor,another)).hasMessage("Synthetic audit failure");assertThat(jdbc.queryForObject("select count(*) from payroll_batch where sacco_id=?",Long.class,institution)).isEqualTo(1);
    }
    @Test void validatesDatesEvidencePrecisionAndScopesStoredRows(){
        var f=batch();f.setMonth("2026-09");assertThatThrownBy(()->service.preview(actor,f)).hasMessage("payroll.error.dates");
        var invalid=batch();invalid.setDeductionDate(TODAY.minusDays(1));assertThatThrownBy(()->service.preview(actor,invalid)).hasMessage("payroll.error.dates");
        assertThatThrownBy(()->instruction(loan,"1.001")).hasMessage("payroll.error.limit");
        jdbc.update("update members set employee_number=null where id=?",client);assertThatThrownBy(()->instruction(loan,"100.00")).hasMessage("payroll.error.employeeMissing");
    }
    @Test void registrySortsAndPaginationRemainScopedAndBounded(){
        instruction(loan,"100.00");var f=batch();f.setPreviewHash(service.preview(actor,f).hash());service.freeze(actor,f);
        for(var sort:List.of("reference","employer","month","date","state","total"))assertThat(service.batches(actor,"","","",0,sort,"desc").rows()).hasSize(1);
        for(var sort:List.of("employer","loan","client","employee","limit","from","to","state"))assertThat(service.instructions(actor,"",0,sort,"asc").rows()).hasSize(1);
        assertThat(service.batches(actor(institution,"B2"),"","","",0,"month","asc").rows()).isEmpty();
        for(int n=0;n<26;n++){var e=new EmployerForm();e.setName("Paged Employer "+n);e.setAgreementReference("Synthetic agreement");service.createEmployer(actor,e);}
        assertThat(service.employers(actor,"",0,"name","asc").rows()).hasSize(25);assertThat(service.employers(actor,"",0,"name","asc").hasNext()).isTrue();assertThat(service.employers(actor,"",1,"name","asc").rows()).hasSize(2);
    }
    UUID instruction(UUID loan,String limit){var f=new InstructionForm();f.setEmployerId(employer);f.setLoanId(loan);f.setMonthlyLimit(new BigDecimal(limit));f.setEffectiveFrom(TODAY.minusMonths(1));f.setEffectiveTo(TODAY.plusYears(1));f.setEvidence("Synthetic signed borrower mandate\nSynthetic employer evidence");return service.createInstruction(actor,f);}
    BatchForm batch(){var f=new BatchForm();f.setEmployerId(employer);f.setMonth("2026-10");f.setDeductionDate(LocalDate.of(2026,10,31));return f;}
    AppUserPrincipal actor(String tenant,String branch){UUID id=member(branch,null);return new AppUserPrincipal(Member.builder().id(id).saccoId(tenant).stationId(branch).memberNo(id.toString()).fullName("Synthetic accountant").position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),true);}
    UUID member(String branch,String employee){UUID id=UUID.randomUUID();jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,is_member,password_hash,employee_number,created_at) values(?,?,?,?,'Synthetic Payroll Employee','ACTIVE','MEMBER',true,'not-a-login',?,now())",id,institution,branch,id.toString(),employee);return id;}
    UUID loan(UUID borrower,String branch){
        UUID id=UUID.randomUUID();long number=NUMBERS.incrementAndGet();
        jdbc.update("insert into loan_applications(id,application_number,loan_id,sacco_id,station_id,applicant_member_id,loan_type,amount,tenor_months,status,required_guarantors,form_data,policy_snapshot,financial_snapshot,created_at,updated_at,version) values(?,?,?,?,?,?,'DEVELOPMENT_LOAN',300,3,'DISBURSED',0,'{}','{}','{}',now(),now(),0)",id,number,Long.toString(number),institution,branch,borrower);
        jdbc.update("insert into loan_ledgers(loan_application_id,sacco_id,station_id,loan_id,applicant_member_id,disbursement_date,principal,created_at) values(?,?,?,?,?,'2026-08-01',300,now())",id,institution,branch,Long.toString(number),borrower);
        for(int n=1;n<=3;n++)jdbc.update("insert into loan_ledger_installments(id,loan_application_id,installment_number,due_date,principal,interest,principal_paid,interest_paid) values(?,?,?,?,100,10,?,?)",UUID.randomUUID(),id,n,LocalDate.of(2026,8+n,20),n==2?10:0,n==2?2:0);
        return id;
    }
    @Configuration(proxyBeanMethods=false) @EnableTransactionManagement static class Config {
        @Bean(destroyMethod="close") HikariDataSource dataSource(){var d=new HikariDataSource();d.setJdbcUrl(System.getenv("MICROFINANCE_PAYROLL_TEST_URL"));d.setUsername("microfinance_test");d.setMaximumPoolSize(5);Flyway.configure().dataSource(d).locations("classpath:db/migration").load().migrate();return d;}
        @Bean JdbcTemplate jdbc(HikariDataSource d){return new JdbcTemplate(d);}
        @Bean JdbcTransactionManager transactionManager(HikariDataSource d){return new JdbcTransactionManager(d);}
        @Bean PayrollRepository repository(JdbcTemplate j){return new PayrollRepository(j);}
        @Bean GeneralLedgerService guard(){return mock(GeneralLedgerService.class);}
        @Bean AuditService audit(){return mock(AuditService.class);}
        @Bean ApplicationClock clock(){var c=mock(ApplicationClock.class);when(c.today()).thenReturn(TODAY);when(c.currentYearMonth()).thenReturn(YearMonth.from(TODAY));when(c.now()).thenAnswer(i->OffsetDateTime.now(ZoneOffset.ofHours(3)));return c;}
        @Bean PayrollService payroll(PayrollRepository r,GeneralLedgerService g,ApplicationClock c,AuditService a){return new PayrollService(r,g,c,a,JsonMapper.builder().build());}
    }
}
