package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.payroll.dto.PayrollDtos.*;
import com.sacco.mvp.accounting.payroll.dto.PayrollFeedbackDtos.*;
import com.sacco.mvp.accounting.payroll.repository.*;
import com.sacco.mvp.accounting.payroll.service.*;
import com.sacco.mvp.accounting.payroll.PayrollFeedbackWorkbookTest;
import com.sacco.mvp.accounting.batch.repository.ReceiptBatchRepository;
import com.sacco.mvp.accounting.batch.service.*;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.context.annotation.*;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.access.AccessDeniedException;
import tools.jackson.databind.ObjectMapper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class PayrollFeedbackPostingPostgresTest {
    AnnotationConfigApplicationContext context;LoanRecordingPostgresTest f;PayrollService payroll;PayrollFeedbackService feedback;UUID batch,loan,employerId;byte[] content;
    @BeforeAll void start(){context=new AnnotationConfigApplicationContext(LoanRecordingPostgresTest.Config.class,Config.class);f=new LoanRecordingPostgresTest();f.context=context;f.jdbc=context.getBean(JdbcTemplate.class);f.service=context.getBean(LoanRecordingService.class);f.ledger=context.getBean(LoanRepaymentLedgerService.class);payroll=context.getBean(PayrollService.class);feedback=context.getBean(PayrollFeedbackService.class);}
    @AfterAll void close(){if(context!=null)context.close();}
    @BeforeEach void fixture(){
        f.fixture();f.configurePolicy(false);when(context.getBean(ApplicationClock.class).currentYearMonth()).thenReturn(YearMonth.of(2026,10));
        loan=f.disbursed("D-PAYROLL-"+UUID.randomUUID());f.jdbc.update("update members set employee_number='00007' where id=?",f.client);
        var employer=new EmployerForm();employer.setName("Synthetic payroll employer");employer.setAgreementReference("Synthetic agreement");employerId=payroll.createEmployer(f.actor,employer);
        instruction(loan,employerId);var form=new BatchForm();form.setEmployerId(employerId);form.setMonth("2026-10");form.setDeductionDate(LoanRecordingPostgresTest.TODAY);form.setPreviewHash(payroll.preview(f.actor,form).hash());batch=payroll.freeze(f.actor,form);payroll.changeState(f.actor,batch,"SENT","Synthetic actual employer delivery",false);
        content=file("00007","100.01");
    }
    UUID instruction(UUID id,UUID employer){var form=new InstructionForm();form.setEmployerId(employer);form.setLoanId(id);form.setMonthlyLimit(new BigDecimal("1000.00"));form.setEffectiveFrom(LocalDate.of(2026,9,1));form.setEffectiveTo(LocalDate.of(2026,12,31));form.setEvidence("Synthetic signed mandate and employer confirmation");return payroll.createInstruction(f.actor,form);}
    byte[] file(String employee,String amount){return PayrollFeedbackWorkbookTest.workbook(Collections.singletonList(new String[]{payroll.batch(f.actor,batch).reference(),employee,PayrollFeedbackService.reference(batch,f.client),"SUCCESS",amount,"2026-10-08","EMP-RESULT-001"}));}
    UUID upload(){return feedback.upload(f.actor,batch,"results.xlsx",content,"Synthetic employer feedback evidence");}
    RemittanceForm remittance(){var r=new RemittanceForm();r.setMoneyAccountId(f.cash);r.setReceivedDate(LoanRecordingPostgresTest.TODAY);r.setReceivedAmount(new BigDecimal("100.01"));r.setReference("BANK-"+UUID.randomUUID());r.setEvidence("Synthetic verified bank credit and employer remittance");r.setVerified(true);return r;}
    @Test void importsAndPostsThroughTheExistingWorkerAndIndependentReversal(){
        UUID id=upload();assertThat(upload()).isEqualTo(id);assertThat(feedback.rows(f.actor,batch,id,0).rows().getFirst().postingState()).isEqualTo("UNPOSTED");assertThat(f.service.detail(f.actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
        var changedMetadata=PayrollFeedbackWorkbookTest.workbook(Collections.singletonList(new String[]{payroll.batch(f.actor,batch).reference(),"00007",PayrollFeedbackService.reference(batch,f.client),"SUCCESS","100.01","2026-10-08","EMP-RESULT-001"}),b->b.getProperties().getCoreProperties().setCreator("Changed spreadsheet metadata"));
        assertThat(changedMetadata).isNotEqualTo(content);assertThat(feedback.upload(f.actor,batch,"renamed.xlsx",changedMetadata,"Repeated employer evidence")).isEqualTo(id);
        var remit=remittance();UUID receipts=feedback.prepare(f.actor,batch,id,remit);assertThat(feedback.prepare(f.actor,batch,id,remit)).isEqualTo(receipts);remit.setEvidence("Changed evidence");assertThatThrownBy(()->feedback.prepare(f.actor,batch,id,remit)).hasMessage("payroll.feedback.error.retry");
        assertThat(f.service.detail(f.actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");f.processBatch(receipts,false);assertThat(f.batches().get(f.actor,receipts).batch().state()).isEqualTo("READY");f.processBatch(receipts,true);
        var row=feedback.rows(f.actor,batch,id,0).rows().getFirst();assertThat(row.postingState()).isEqualTo("POSTED");assertThat(row.receiptId()).isNotNull();assertThat(f.service.detail(f.actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("919.99");
        var receipt=f.receiptService().find(f.actor,row.receiptId()).orElseThrow();assertThat(receipt.header().total()).isEqualByComparingTo("100.01");assertThat(receipt.rows().getFirst().interest()).isEqualByComparingTo("20.00");
        UUID journal=f.jdbc.queryForObject("select journal_id from accountant_loan_post where id=?",UUID.class,receipt.rows().getFirst().postingId());
        assertThat(f.jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where journal_id=?",BigDecimal.class,journal)).isZero();
        assertThat(f.jdbc.queryForObject("select sum(debit) from gl_journal_line where journal_id=? and account_id=?",BigDecimal.class,journal,f.cash)).isEqualByComparingTo("100.01");
        assertThat(f.jdbc.queryForObject("select sum(credit) from gl_journal_line where journal_id=? and account_id=?",BigDecimal.class,journal,f.principal)).isEqualByComparingTo("80.01");
        assertThat(f.jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Long.class,f.institution)).isEqualTo(2);
        assertThatThrownBy(()->f.receiptService().reverse(f.actor,row.receiptId(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Wrong actor")).hasMessage("voucher.error.independentCorrection");
        f.receiptService().reverse(f.operator("B1"),row.receiptId(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic bank correction");assertThat(feedback.rows(f.actor,batch,id,0).rows().getFirst().postingState()).isEqualTo("REVERSED");assertThat(f.service.detail(f.actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
        assertThatThrownBy(()->feedback.upload(f.actor,batch,"changed.xlsx",file("00007","100.02"),"Duplicate employer evidence")).hasMessage("payroll.feedback.error.duplicate");
    }
    @Test void rejectsMismatchesUnverifiedFundsExcessAndSealedDraftEdits(){
        assertThatThrownBy(()->feedback.upload(f.actor,batch,"results.xlsx",file("OTHER","10"),"Evidence")).hasMessage("payroll.feedback.error.match");assertThatThrownBy(()->feedback.upload(f.actor,batch,"results.xlsx",file("00007","1000.01"),"Evidence")).hasMessage("payroll.feedback.error.amount");
        UUID id=upload();var remittance=remittance();remittance.setVerified(false);assertThatThrownBy(()->feedback.prepare(f.actor,batch,id,remittance)).hasMessage("payroll.feedback.error.remittance");remittance.setVerified(true);remittance.setReceivedAmount(BigDecimal.ONE);assertThatThrownBy(()->feedback.prepare(f.actor,batch,id,remittance)).hasMessage("payroll.feedback.error.remittanceAmount");remittance.setReceivedAmount(new BigDecimal("100.01"));UUID receipts=feedback.prepare(f.actor,batch,id,remittance);
        var item=f.batches().items(f.actor,receipts,0,"").rows().getFirst();assertThatThrownBy(()->f.batches().delete(f.actor,receipts,item.id(),item.version())).hasMessage("batch.error.payroll");assertThatThrownBy(()->f.batches().save(f.actor,receipts,item.id(),new com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.SaveItem(item.version(),item.form()))).hasMessage("batch.error.payroll");assertThatThrownBy(()->f.jdbc.update("update receipt_batch_item set total=1 where id=?",item.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);assertThatThrownBy(()->f.jdbc.update("delete from payroll_feedback_row where feedback_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->payroll.changeState(f.actor,batch,"CANCELLED","Synthetic withdrawal",true)).hasMessage("payroll.error.feedback");
    }
    @Test void scopedReadsAndRevokedClaimsAndConcurrentImportRetry()throws Exception {
        UUID id=upload();assertThatThrownBy(()->feedback.get(f.operator("B2"),batch,id)).isInstanceOf(AccessDeniedException.class);
        when(context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of());assertThatThrownBy(this::upload).isInstanceOf(AccessDeniedException.class);
        when(context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(EnumSet.allOf(UserClaim.class));
        try(var pool=Executors.newFixedThreadPool(2)){var go=new CountDownLatch(1);Callable<UUID> task=()->{go.await();return upload();};var a=pool.submit(task);var b=pool.submit(task);go.countDown();assertThat(a.get(30,TimeUnit.SECONDS)).isEqualTo(b.get(30,TimeUnit.SECONDS));}
        for(var sort:List.of("file","date","actor","rows","total"))assertThat(feedback.list(f.actor,batch,0,sort,"asc").rows()).hasSize(1);
    }
    @Test void remittanceAuditFailureRollsBackReceiptPreparation(){UUID id=upload();long count=f.jdbc.queryForObject("select count(*) from receipt_batch where sacco_id=?",Long.class,f.institution);doThrow(new IllegalStateException("Synthetic audit failure")).when(context.getBean(AuditService.class)).logEvent(anyString(),any(),eq("PAYROLL_REMITTANCE_VERIFIED"),any(),any(),anyString(),anyString(),anyString(),anyString(),anyString(),any());assertThatThrownBy(()->feedback.prepare(f.actor,batch,id,remittance())).hasMessage("Synthetic audit failure");assertThat(f.jdbc.queryForObject("select count(*) from receipt_batch where sacco_id=?",Long.class,f.institution)).isEqualTo(count);assertThat(feedback.get(f.actor,batch,id).receiptBatchId()).isNull();}
    @Test void partialDeductionAcrossTwoLoansAndFailedEmployeeProduceOnlyTheSuccessfulReceipt(){
        UUID second=f.disbursed("D-PAYROLL-SECOND");instruction(second,employerId);
        UUID failedClient=UUID.randomUUID();f.jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,is_member,password_hash,employee_number,created_at) values(?,?,'B1',?,'Failed employee','ACTIVE','MEMBER',true,'test-only','00008',now())",failedClient,f.institution,failedClient.toString());var form=f.form();form.setClientId(failedClient);UUID third=f.service.save(f.actor,form);f.service.disburse(f.actor,f.post(third,"1000.00","D-PAYROLL-THIRD"));instruction(third,employerId);
        payroll.changeState(f.actor,batch,"CANCELLED","Synthetic employer withdrawal",true);var replacement=new BatchForm();replacement.setReplacesId(batch);replacement.setEmployerId(employerId);replacement.setMonth("2026-10");replacement.setDeductionDate(LoanRecordingPostgresTest.TODAY);replacement.setPreviewHash(payroll.preview(f.actor,replacement).hash());batch=payroll.freeze(f.actor,replacement);payroll.changeState(f.actor,batch,"SENT","Synthetic replacement delivery",false);
        content=PayrollFeedbackWorkbookTest.workbook(Arrays.asList(new String[]{payroll.batch(f.actor,batch).reference(),"00007",PayrollFeedbackService.reference(batch,f.client),"SUCCESS","1100.00","2026-10-08","SUCCESS-RESULT"},new String[]{payroll.batch(f.actor,batch).reference(),"00008",PayrollFeedbackService.reference(batch,failedClient),"FAILED","0","","FAILED-RESULT"}));
        UUID id=upload();var remit=remittance();remit.setReceivedAmount(new BigDecimal("1100.00"));UUID receipts=feedback.prepare(f.actor,batch,id,remit);assertThat(f.batches().get(f.actor,receipts).receipts()).isEqualTo(1);var item=f.batches().items(f.actor,receipts,0,"").rows().getFirst();assertThat(item.form().getRows()).hasSize(2);assertThat(item.form().getRows().stream().map(r->r.getAmount()).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo("1100.00");
        f.processBatch(receipts,false);f.processBatch(receipts,true);assertThat(f.batches().get(f.actor,receipts).posted()).isEqualTo(1);assertThat(f.service.detail(f.actor,third).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");assertThat(feedback.rows(f.actor,batch,id,0).rows()).anyMatch(r->r.status().equals("FAILED") && r.postingState().equals("UNPOSTED"));
    }
    @Test void changedBalancesAfterValidationBlockPostingWithoutOverwritingHistory(){
        UUID id=upload();UUID receipts=feedback.prepare(f.actor,batch,id,remittance());f.processBatch(receipts,false);f.service.repay(f.actor,f.post(loan,"30.00","INTERVENING-REPAYMENT"));f.processBatch(receipts,true);
        assertThat(f.batches().get(f.actor,receipts).posted()).isZero();assertThat(f.batches().get(f.actor,receipts).failed()).isEqualTo(1);assertThat(feedback.rows(f.actor,batch,id,0).rows().getFirst().postingState()).isEqualTo("FAILED");assertThat(f.service.detail(f.actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("990.00");
    }
    @Test void concurrentFreshUploadAndPreparationKeepOneFinancialSource()throws Exception {
        try(var pool=Executors.newFixedThreadPool(2)){
            var go=new CountDownLatch(1);Callable<UUID> task=()->{go.await();return upload();};var first=pool.submit(task);var second=pool.submit(task);go.countDown();UUID id=first.get(30,TimeUnit.SECONDS);assertThat(second.get(30,TimeUnit.SECONDS)).isEqualTo(id);
            var remit=remittance();var prepareGo=new CountDownLatch(1);Callable<UUID> prepare=()->{prepareGo.await();return feedback.prepare(f.actor,batch,id,remit);};var a=pool.submit(prepare);var b=pool.submit(prepare);prepareGo.countDown();assertThat(a.get(30,TimeUnit.SECONDS)).isEqualTo(b.get(30,TimeUnit.SECONDS));
            assertThat(feedback.remittance(f.actor,batch,id).orElseThrow().reference()).isEqualTo(remit.getReference());
            assertThat(f.jdbc.queryForObject("select count(*) from receipt_batch where sacco_id=?",Long.class,f.institution)).isEqualTo(1);
        }
        for(var role:Position.values())assertThat(UserClaim.defaultClaims(Set.of(role),false)).doesNotContain(UserClaim.PAYROLL_DEDUCTIONS_IMPORT,UserClaim.PAYROLL_DEDUCTIONS_POST);
    }
    @Test void failedDeductionCanBeFollowedByOneSuccessfulFinalResult(){
        var failed=PayrollFeedbackWorkbookTest.workbook(Collections.singletonList(new String[]{payroll.batch(f.actor,batch).reference(),"00007",PayrollFeedbackService.reference(batch,f.client),"FAILED","0","","FAILED-RESULT-001"}));UUID first=feedback.upload(f.actor,batch,"failed.xlsx",failed,"Synthetic failed payroll result");assertThat(feedback.get(f.actor,batch,first).total()).isZero();assertThatThrownBy(()->feedback.prepare(f.actor,batch,first,remittance())).hasMessage("payroll.feedback.error.remittanceAmount");
        UUID next=upload();assertThat(next).isNotEqualTo(first);assertThat(feedback.get(f.actor,batch,next).total()).isEqualByComparingTo("100.01");assertThat(f.jdbc.queryForObject("select count(*) from accountant_loan_post where sacco_id=? and kind='PAYMENT'",Long.class,f.institution)).isZero();
    }
    @Configuration(proxyBeanMethods=false) static class Config {
        @Bean PayrollRepository payrollRepository(JdbcTemplate j){return new PayrollRepository(j);}
        @Bean PayrollFeedbackRepository feedbackRepository(JdbcTemplate j){return new PayrollFeedbackRepository(j);}
        @Bean PayrollFeedbackWorkbook workbook(){return new PayrollFeedbackWorkbook();}
        @Bean PayrollService payroll(PayrollRepository r,GeneralLedgerService g,ApplicationClock c,AuditService a,ObjectMapper json){return new PayrollService(r,g,c,a,json);}
        @Bean PayrollFeedbackService feedback(PayrollService p,PayrollRepository r,PayrollFeedbackRepository f,PayrollFeedbackWorkbook w,ReceiptBatchService b,ReceiptBatchRepository br,LoanRecordingService l,ApplicationClock c,AuditService a,ObjectMapper json){return new PayrollFeedbackService(p,r,f,w,b,br,l,c,a,json);}
    }
}
