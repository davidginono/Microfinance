package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.dao.DataAccessException;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class GroupOrganizationFundingPostgresTest {
    final GroupOrganizationPayoutPostgresTest p=new GroupOrganizationPayoutPostgresTest();
    final GroupOrganizationAgreementPostgresTest s=p.s;
    final GroupOrganizationPostgresTest o=s.o;
    final LoanRecordingPostgresTest f=o.f;
    @BeforeAll void start(){p.start();}
    @AfterAll void stop(){p.stop();}
    @BeforeEach void fixture(){p.fixture();p.ready();assertThat(p.service.confirm(s.signer,s.cycle,s.hash,true,"")).isNull();
        f.context.getBean(MemberDirectoryService.class).find(f.actor.getMemberId()).orElseThrow().setSignatureText("Synthetic lender signature");
        f.jdbc.update("update members set signature_text='Synthetic lender signature' where id=?",f.actor.getMemberId());}
    com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.PostForm funding(){var form=f.post(s.loan,"1000.00","ORG-FUND-"+UUID.randomUUID());form.setOrganizationPacketHash(s.hash);form.setOrganizationAccepted(true);form.setEvidence("Synthetic signed agreement and verified organization payout");return form;}
    LoanRepaymentLedgerService ledger(){return f.context.getBean(LoanRepaymentLedgerService.class);}
    @Test void lenderExecutionFundsOneCompanyAssetAndReusesRepaymentAndIndependentCorrection(){
        var form=funding();var funded=f.service.disburse(f.actor,form);assertThat(f.service.disburse(f.actor,form).id()).isEqualTo(funded.id());
        assertThat(o.status(s.loan)).isEqualTo("DISBURSED");assertThat(o.count("loan_ledgers")).isEqualTo(1);assertThat(o.count("lending_group_org_funding")).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("select applicant_member_id from loan_ledgers where loan_application_id=?",UUID.class,s.loan)).isNull();
        assertThat(f.service.detail(f.actor,s.loan).loan().client()).isEqualTo("Synthetic organization <Company>");
        var received=f.post(s.loan,"120.00","ORG-PAYMENT");received.setInterestAccountId(f.interest);var payment=f.service.repay(f.actor,received);
        assertThat(payment.principal()).isEqualByComparingTo("120.00");assertThat(payment.interest()).isZero();assertThat(payment.principalBalance()).isEqualByComparingTo("880.00");
        assertThatThrownBy(()->f.service.reverse(f.actor,payment.id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Self correction")).hasMessage("recording.error.independentCorrection");
        var checker=f.operator("B1");var key=UUID.randomUUID();var correction=f.service.reverse(checker,payment.id(),key,LoanRecordingPostgresTest.TODAY,"Synthetic independent company receipt correction");
        assertThat(f.service.reverse(checker,payment.id(),key,LoanRecordingPostgresTest.TODAY,"Synthetic independent company receipt correction").id()).isEqualTo(correction.id());
        assertThat(f.service.detail(f.actor,s.loan).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
        assertThat(o.count("gl_journal")).isEqualTo(3);assertThat(f.jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where sacco_id=?",BigDecimal.class,f.institution)).isZero();
        assertThatThrownBy(()->ledger().view(s.loan,s.signer,0,0)).isInstanceOf(AccessDeniedException.class);
        assertThat(o.count("lending_group_guarantee")).isZero();
        var report=new com.sacco.mvp.accounting.reports.LoanPortfolioReportRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(f.jdbc));
        var summary=report.summary(new com.sacco.mvp.accounting.reports.LoanPortfolioReportService.Scope(f.institution,"B1",false),
            new com.sacco.mvp.accounting.reports.LoanPortfolioReportService.Parameters(LoanRecordingPostgresTest.TODAY.minusMonths(1),LoanRecordingPostgresTest.TODAY,OffsetDateTime.now().plusMinutes(1),0));
        assertThat(summary.covered()).isEqualTo(1);assertThat(summary.excluded()).isZero();assertThat(summary.principal()).isEqualByComparingTo("1000.00");
    }
    @Test void fullEarlySettlementAndCorrectionKeepContractAndInterestExclusions(){
        f.service.disburse(f.actor,funding());var payment=funding();payment.setRequestKey(UUID.randomUUID());payment.setReference("ORG-SETTLEMENT");payment.setInterestAccountId(f.interest);
        var paid=f.service.repay(f.actor,payment);assertThat(o.status(s.loan)).isEqualTo("PAID");assertThat(paid.interest()).isZero();
        assertThat(f.service.detail(f.actor,s.loan).contractualInterest()).isEqualByComparingTo("20.00");
        f.service.reverse(f.operator("B1"),paid.id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic settlement correction");
        assertThat(o.status(s.loan)).isEqualTo("DISBURSED");assertThat(f.service.detail(f.actor,s.loan).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
    }
    @Test void missingSignatureChangedTermsAndRevokedCurrentExecutionClaimCannotFund(){
        var form=funding();form.setOrganizationAccepted(false);assertThatThrownBy(()->f.service.disburse(f.actor,form)).hasMessage("recording.error.organizationFundingPending");
        form.setOrganizationAccepted(true);form.setFirstPaymentDate(LoanRecordingPostgresTest.TODAY.plusMonths(2));assertThatThrownBy(()->f.service.disburse(f.actor,form)).hasMessage("recording.error.organizationTerms");form.setFirstPaymentDate(null);
        var actor=f.context.getBean(MemberDirectoryService.class).find(f.actor.getMemberId()).orElseThrow();actor.setSignatureText(null);assertThatThrownBy(()->f.service.disburse(f.actor,form)).hasMessage("recording.error.organizationSignature");actor.setSignatureText("Synthetic lender signature");
        var claims=EnumSet.allOf(UserClaim.class);claims.remove(UserClaim.GROUP_LENDING_EXECUTE);when(f.context.getBean(UserClaimService.class).effectiveClaims(eq(f.actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(claims);
        assertThatThrownBy(()->f.service.disburse(f.actor,form)).isInstanceOf(AccessDeniedException.class);assertThat(o.count("gl_journal")).isZero();assertThat(o.count("loan_ledgers")).isZero();
    }
    @Test void revokedPayoutAuthorityAndForeignBranchCannotFundASignedAgreement(){
        assertThatThrownBy(()->f.service.disburse(f.operator("B2"),funding())).isInstanceOf(AccessDeniedException.class);
        o.authorities.revoke(f.actor,o.group,o.authority,"Synthetic authority revoked after payout confirmation");
        assertThatThrownBy(()->f.service.disburse(f.actor,funding())).hasMessage("recording.error.organizationFundingPending");assertThat(o.count("loan_ledgers")).isZero();
    }
    @Test void auditFailureRollsBackLenderSignatureLedgerAndCash(){
        doThrow(new IllegalStateException("Synthetic lender audit failure")).when(f.context.getBean(AuditService.class)).logEvent(eq("GROUP_LENDING"),eq(s.cycle),eq("ORGANIZATION_LENDER_SIGNED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->f.service.disburse(f.actor,funding())).hasMessage("Synthetic lender audit failure");
        for(var table:List.of("lending_group_org_funding","loan_ledgers","gl_journal","accountant_loan_post"))assertThat(o.count(table)).isZero();assertThat(o.status(s.loan)).isEqualTo("READY_FOR_DISBURSEMENT");
    }
    @Test void concurrentIdenticalFundingRequestsPostOnce()throws Exception{
        var form=funding();try(var workers=Executors.newFixedThreadPool(2)){var gate=new CountDownLatch(1);var one=workers.submit(()->{gate.await();return f.service.disburse(f.actor,form).id();});var two=workers.submit(()->{gate.await();return f.service.disburse(f.actor,form).id();});gate.countDown();assertThat(one.get(45,TimeUnit.SECONDS)).isEqualTo(two.get(45,TimeUnit.SECONDS));}
        assertThat(o.count("lending_group_org_funding")).isEqualTo(1);assertThat(o.count("gl_journal")).isEqualTo(1);
    }
    @Test void closedPeriodCannotCommitTheLenderExecution(){
        f.jdbc.update("insert into accounting_period(id,sacco_id,starts_on,ends_on,state,created_by,created_at) values(?,?,'2026-10-01','2026-10-31','CLOSED',?,now())",UUID.randomUUID(),f.institution,f.actor.getMemberId());
        assertThatThrownBy(()->f.service.disburse(f.actor,funding())).hasMessage("voucher.error.period");assertThat(o.count("lending_group_org_funding")).isZero();assertThat(o.count("loan_ledgers")).isZero();
    }
    @Test void fundingEvidenceIsImmutableAndFundedCycleCannotBeWithdrawn(){
        f.service.disburse(f.actor,funding());assertThatThrownBy(()->p.withdraw()).isInstanceOf(RuntimeException.class);
        for(var sql:List.of("delete from lending_group_org_funding where cycle_id=?","update lending_group_org_funding set packet_hash=repeat('0',64) where cycle_id=?"))assertThatThrownBy(()->f.jdbc.update(sql,s.cycle)).isInstanceOf(DataAccessException.class);
        assertThat(o.status(s.loan)).isEqualTo("DISBURSED");assertThat(o.count("lending_group_org_funding")).isEqualTo(1);
    }
    @Test void independentFundingCorrectionRetainsTheCompanyContractAndExecution(){
        f.service.disburse(f.actor,funding());var request=f.adjustments().request(f.actor,f.adjustment(s.loan,"DISBURSEMENT_REVERSAL"));
        f.adjustments().decide(f.operator("B1"),request,UUID.randomUUID(),true,"Synthetic verified organization payout returned");
        assertThat(o.status(s.loan)).isEqualTo("DISBURSEMENT_REVERSED");assertThat(f.service.detail(f.actor,s.loan).loan().outstandingPrincipal()).isZero();
        assertThat(o.count("lending_group_org_funding")).isEqualTo(1);assertThat(o.count("lending_group_org_payout")).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("select sum(z.debit-z.credit) from gl_journal_line z where z.sacco_id=? and z.account_id=?",BigDecimal.class,f.institution,f.principal)).isZero();
    }
    @Test void postJournalAuditFailureRollsBackExecutionAndAllLedgerRows(){
        doThrow(new IllegalStateException("Synthetic funding posting audit failure")).when(f.context.getBean(AuditService.class)).logEvent(eq("LOAN_RECORDING"),any(),eq("LOAN_DISBURSEMENT_POSTED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->f.service.disburse(f.actor,funding())).hasMessage("Synthetic funding posting audit failure");
        for(var table:List.of("lending_group_org_funding","loan_ledgers","gl_journal","accountant_loan_post"))assertThat(o.count(table)).isZero();
        assertThat(f.jdbc.queryForObject("select count(*) from loan_ledger_installments where loan_application_id=?",Integer.class,s.loan)).isZero();
    }
    @Test void fundingCannotBeBackdatedBeforeActualAgreementAndPayoutConfirmation(){
        var form=funding();form.setEffectiveDate(LoanRecordingPostgresTest.TODAY.minusDays(1));assertThatThrownBy(()->f.service.disburse(f.actor,form)).isInstanceOf(IllegalArgumentException.class);
        assertThat(o.count("lending_group_org_funding")).isZero();assertThat(o.count("gl_journal")).isZero();
    }
    @Test void unifiedReceiptRetainsTypedBorrowerActualPayerStatementsAndWholeCorrection(){
        f.service.disburse(f.actor,funding());var company=f.service.detail(f.actor,s.loan).loan().clientId();
        var form=f.receiptForm(s.loan);form.setClientId(company);form.setLoanPayer("Synthetic project trading income / cashier A");
        var preview=f.receiptService().preview(f.actor,form);assertThat(preview).hasSize(1);assertThat(preview.getFirst().principal()).isEqualByComparingTo("120.00");
        var saved=f.receiptService().post(f.actor,form);assertThat(f.receiptService().post(f.actor,form).header().id()).isEqualTo(saved.header().id());
        assertThat(saved.payer()).isEqualTo(form.getLoanPayer());assertThat(saved.header().party()).isEqualTo("Synthetic organization <Company>");
        assertThat(f.jdbc.queryForMap("select borrower_party_id,client_id from accountant_loan_receipt where id=?",saved.header().id())).containsEntry("borrower_party_id",company).containsEntry("client_id",null);
        assertThat(f.service.statement(f.actor,company,0).principal()).isEqualByComparingTo("880.00");assertThat(f.service.movements(f.actor,company,0).rows()).hasSize(2);
        assertThat(f.service.loanStatement(f.actor,company,s.loan,0,false).source().terms()).hasSize(1);
        assertThat(f.service.statement(f.actor,f.client,0).page().rows()).isEmpty();
        assertThatThrownBy(()->f.service.statement(f.operator("B2"),company,0)).isInstanceOf(AccessDeniedException.class);
        var checker=f.operator("B1");var reversed=f.receiptService().reverse(checker,saved.header().id(),UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic company receipt correction");
        assertThat(reversed.payer()).isEqualTo(saved.payer());assertThat(f.service.statement(f.actor,company,0).principal()).isEqualByComparingTo("1000.00");assertThat(o.count("gl_journal")).isEqualTo(3);
        assertThat(f.receiptService().export(f.actor,saved.header().id()).rows()).hasSize(1);
    }
    @Test void companyReceiptRequiresPayerAndCannotChargeARepresentativePersonalStatement(){
        f.service.disburse(f.actor,funding());var company=f.service.detail(f.actor,s.loan).loan().clientId();var form=f.receiptForm(s.loan);form.setClientId(company);
        assertThatThrownBy(()->f.receiptService().preview(f.actor,form)).hasMessage("voucher.error.text");form.setLoanPayer("Synthetic actual payer");form.setClientId(f.client);
        assertThatThrownBy(()->f.receiptService().post(f.actor,form)).hasMessage("recording.error.client");assertThat(o.count("accountant_loan_receipt")).isZero();assertThat(f.service.detail(f.actor,s.loan).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
    }
    @Test void deductedChargesConserveGrossPrincipalAndOnlyTheAuthorizedNetCash(){
        p.withdraw();f.chargePolicy("DEDUCT");o.authority=o.authorities.record(f.actor,o.group,o.authorityForm());
        var form=o.form();form.getOrganization().setInsurancePremium(new BigDecimal("10.00"));form.getOrganization().setInsuranceEvidence("Synthetic insurer premium checked");
        s.cycle=o.create(form);s.loan=o.service.submit(f.actor,s.cycle,form);s.hash=s.service.packet(s.signer,s.cycle,0).hash();s.openDocument(s.signer,s.cycle);p.ready();
        assertThat(p.service.confirm(s.signer,s.cycle,s.hash,true,"")).isNull();var funded=f.service.disburse(f.actor,funding());
        assertThat(f.jdbc.queryForObject("select sum(credit-debit) from gl_journal_line where journal_id=? and account_id=?",BigDecimal.class,funded.journalId(),f.cash)).isEqualByComparingTo("970.00");
        assertThat(f.service.detail(f.actor,s.loan).loan().principal()).isEqualByComparingTo("1000.00");assertThat(f.service.detail(f.actor,s.loan).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
        assertThat(f.jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where sacco_id=?",BigDecimal.class,f.institution)).isZero();
    }
    @Test void fundingAndWithdrawalRaceHasExactlyOneWinner()throws Exception{
        var form=funding();try(var workers=Executors.newFixedThreadPool(2)){var gate=new CountDownLatch(1);
            var fund=workers.submit(()->{gate.await();try{f.service.disburse(f.actor,form);return true;}catch(RuntimeException closed){return false;}});
            var withdraw=workers.submit(()->{gate.await();try{p.withdraw();return true;}catch(RuntimeException closed){return false;}});gate.countDown();
            boolean funded=fund.get(45,TimeUnit.SECONDS),ended=withdraw.get(45,TimeUnit.SECONDS);assertThat(funded).isNotEqualTo(ended);
            assertThat(o.status(s.loan)).isEqualTo(funded?"DISBURSED":"WITHDRAWN");assertThat(o.count("loan_ledgers")).isEqualTo(funded?1:0);assertThat(o.count("lending_group_org_funding")).isEqualTo(funded?1:0);
        }
    }
}
