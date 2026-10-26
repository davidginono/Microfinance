package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.loans.repository.LoanRecordingRepository;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.loans.repository.LoanPostingAuthorizationRepository;
import com.sacco.mvp.accounting.loans.service.LoanPostingGuard;
import com.sacco.mvp.accounting.repository.*;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
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

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_(recording|flexible_repayment_[a-f0-9]{8})_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoanRecordingPostgresTest {
    static final LocalDate TODAY=LocalDate.of(2026,10,8);
    AnnotationConfigApplicationContext context;JdbcTemplate jdbc;LoanRecordingService service;LoanRepaymentLedgerService ledger;
    String institution;AppUserPrincipal actor;UUID client,product,cash,principal,interest;
    com.sacco.mvp.accounting.service.LoanReceiptService receiptService(){return context.getBean(com.sacco.mvp.accounting.service.LoanReceiptService.class);}
    com.sacco.mvp.accounting.dto.VoucherDtos.Form receiptForm(UUID... ids){var f=new com.sacco.mvp.accounting.dto.VoucherDtos.Form();f.setReceiptPurpose("LOAN_REPAYMENT");f.setEffectiveDate(TODAY);f.setClientId(client);f.setMoneyAccountId(cash);f.setInterestAccountId(interest);f.setReference("GROUP-"+UUID.randomUUID());var rows=new ArrayList<com.sacco.mvp.accounting.dto.VoucherDtos.RowForm>();for(var id:ids){var r=new com.sacco.mvp.accounting.dto.VoucherDtos.RowForm();r.setLoanId(id);r.setAmount(new BigDecimal("120.00"));r.setDescription("Loan collection");rows.add(r);}f.setRows(rows);return f;}
    UUID disbursed(String reference){var id=service.save(actor,form());service.disburse(actor,post(id,"1000.00",reference));return id;}
    UUID topUpSource(){var f=form();f.setMonths(2);var id=service.save(actor,f);service.disburse(actor,post(id,"1000.00","D-TOPUP-"+UUID.randomUUID()));return id;}
    UUID recordTopUp(UUID source){var f=form();f.setTopUpSourceLoanId(source);f.setApplicationDate(TODAY);f.setFirstPaymentDate(null);f.setPrincipal(new BigDecimal("1500.00"));f.setEvidence("Synthetic signed top-up agreement");return service.save(actor,f);}
    PostForm topUpPost(UUID loan){var f=post(loan,"1500.00","TOPUP-"+UUID.randomUUID());f.setFirstPaymentDate(TODAY.plusMonths(1));f.setInterestAccountId(interest);f.setEvidence("Synthetic signed top-up and verified bank transfer");var q=service.disbursementTopUp(actor,loan,TODAY).orElseThrow();f.setSettlementSequence(q.sequence());f.setSettlementPrincipal(q.principal());f.setSettlementInterest(q.interest());return f;}
    com.sacco.mvp.accounting.charges.service.LoanChargeService chargeService(){return context.getBean(com.sacco.mvp.accounting.charges.service.LoanChargeService.class);}
    UUID chargeAccount; UUID managementAccount; UUID insuranceAccount;
    void chargePolicy(String timing){chargePolicy(timing,false,null);}
    void chargePolicy(String timing,boolean allowCustom,BigDecimal limit){
        chargeAccount=account("CHARGES","ASSET","DEBIT","POSTING","FEE_RECEIVABLE");
        managementAccount=account("DEFERRED","LIABILITY","CREDIT","POSTING","CLEARING");
        insuranceAccount=account("INSURER","LIABILITY","CREDIT","POSTING","PAYABLE");
        var f=new com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.PolicyForm();f.setProductId(product);f.setEffectiveFrom(LocalDate.of(2026,1,1));f.setPolicyReference("Synthetic approved policy reference");f.setAgreementReference("Synthetic approved bilingual agreement");f.setInsurer("Synthetic licensed insurer");f.setInsurerVerification("Synthetic licence and premium evidence");f.setEnabled(true);f.setAllowCustom(allowCustom);f.setCustomManagementLimit(limit);
        f.setReceivableAccountId(chargeAccount);f.setManagementAccountId(managementAccount);f.setInsuranceAccountId(insuranceAccount);f.setManagementTiming(com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Timing.valueOf(timing));f.setInsuranceTiming(com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Timing.valueOf(timing));chargeService().savePolicy(actor,f);
        jdbc.update("update loan_product_settings set application_fee=10,processing_fee_rate=0.01,insurance_rate=0.01 where id=?",product);
    }
    UUID chargedLoan(){var f=form();f.setInsurancePremium(new BigDecimal("10.00"));f.setInsuranceEvidence("Synthetic actual insurer premium 10.00 / "+UUID.randomUUID());f.setEvidence("Synthetic signed agreement");return service.save(actor,f);}
    PostForm chargedFunding(UUID id){var f=post(id,"1000.00","D-CHARGED-"+UUID.randomUUID());f.setEvidence("Synthetic signed agreement and bank transfer");return f;}
    List<com.sacco.mvp.accounting.statements.dto.StatementModels.Activity> statementActivity(UUID account){
        var repository=new com.sacco.mvp.accounting.statements.repository.StatementRepository(jdbc);
        var period=new com.sacco.mvp.accounting.statements.dto.StatementModels.Parameters(LocalDate.of(2026,9,1),TODAY,false);
        var run=UUID.randomUUID();var cutoff=OffsetDateTime.now();repository.create(run,actor,period,cutoff,UUID.randomUUID());repository.capture(run,actor,period,cutoff);
        return repository.activity(run,account,period,0,5001);
    }
    @Test void statementAccountNumbersFollowLoansAcrossFundingFeesRepaymentsReversalsAndAdjustments(){
        chargePolicy("SEPARATE");var loan=chargedLoan();service.disburse(actor,chargedFunding(loan));
        var payment=post(loan,"120.00","STATEMENT-PAYMENT");payment.setInterestAccountId(interest);
        var paid=service.repay(actor,payment);service.reverse(operator("B1"),paid.id(),UUID.randomUUID(),TODAY,"Synthetic receipt correction");
        var adjustment=provide(loan,"100.00");String number=service.detail(actor,loan).loan().number();
        for(var account:List.of(cash,principal,chargeAccount)){
            var activity=statementActivity(account);assertThat(activity).isNotEmpty();
            assertThat(activity).extracting(com.sacco.mvp.accounting.statements.dto.StatementModels.Activity::accountNumber).containsOnly(number);
            assertThat(activity).extracting(com.sacco.mvp.accounting.statements.dto.StatementModels.Activity::journalId).doesNotHaveDuplicates();
            assertThat(activity.stream().map(r->r.debit().subtract(r.credit())).reduce(BigDecimal.ZERO,BigDecimal::add))
                .isEqualByComparingTo(jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where sacco_id=? and station_id='B1' and account_id=?",BigDecimal.class,institution,account));
        }
        var provisionJournal=jdbc.queryForObject("select journal_id from loan_adjustment_decision where request_id=?",UUID.class,adjustment);
        var provisionAccount=jdbc.queryForObject("select debit_account_id from loan_adjustment_request where id=?",UUID.class,adjustment);
        assertThat(statementActivity(provisionAccount)).anySatisfy(r->{assertThat(r.journalId()).isEqualTo(provisionJournal);assertThat(r.accountNumber()).isEqualTo(number);});
    }
    @Test void outstandingLoansDrillDownIncludesProductAccountsAndProvisionsAndReconcilesLoanIds(){
        UUID otherProduct=UUID.randomUUID();
        jdbc.update("""
            insert into loan_product_settings(id,sacco_id,loan_type,active,product_status,product_code,product_name,form_schema,
            guarantors_required,interest_rate,interest_method,repayment_frequency,min_repayment_months,max_repayment_months,created_at,updated_at)
            select ?,sacco_id,loan_type,active,product_status,'SECOND','Second business loan',form_schema,
            guarantors_required,interest_rate,interest_method,repayment_frequency,min_repayment_months,max_repayment_months,now(),now()
            from loan_product_settings where id=?
            """,otherProduct,product);
        jdbc.update("update loan_product_settings set manager_review_required=false,loan_officer_review_required=false,chairperson_review_required=false,board_review_required=false,committee_review_required=false,accountant_review_required=false,disbursement_officer_required=true,disbursement_proof_required=false where id=?",otherProduct);
        UUID secondPrincipal=account("SECOND-LOANS","ASSET","DEBIT","POSTING","LOAN_PRINCIPAL");
        jdbc.update("update loan_product_settings set principal_gl_account_id=?,interest_gl_account_id=? where id=?",secondPrincipal,interest,otherProduct);
        var first=disbursed("D-FIRST-PRODUCT");var secondForm=form();secondForm.setProductId(otherProduct);var second=service.save(actor,secondForm);
        var funding=post(second,"1000.00","D-SECOND-PRODUCT");funding.setPrincipalAccountId(secondPrincipal);service.disburse(actor,funding);
        for(var id:List.of(first,second)){var payment=post(id,"120.00","PRODUCT-COLLECTION-"+id);payment.setPrincipalAccountId(id.equals(first)?principal:secondPrincipal);payment.setInterestAccountId(interest);service.repay(actor,payment);}
        UUID adjustment=provide(first,"100.00");UUID allowance=jdbc.queryForObject("select credit_account_id from loan_adjustment_request where id=?",UUID.class,adjustment);
        var repository=new com.sacco.mvp.accounting.statements.repository.StatementRepository(jdbc);
        var period=new com.sacco.mvp.accounting.statements.dto.StatementModels.Parameters(LocalDate.of(2026,9,1),TODAY,false);
        var now=OffsetDateTime.now();String group="Outstanding clients' loans";
        for(UUID account:List.of(principal,secondPrincipal,allowance))repository.map(actor,new com.sacco.mvp.accounting.statements.dto.StatementModels.Mapping(account,group,"CURRENT_ASSETS","OPERATING","",4,""),null,now);
        UUID run=UUID.randomUUID();repository.create(run,actor,period,now,UUID.randomUUID());repository.capture(run,actor,period,now);
        var balances=repository.balances(run,institution,period);var calculated=com.sacco.mvp.accounting.statements.service.StatementCalculator.calculate(balances,repository.cash(run,period),period);
        var snapshot=new com.sacco.mvp.accounting.statements.dto.StatementModels.Snapshot(1,run,period,now,institution,"B1","Synthetic institution","Branch",balances,calculated.position(),calculated.income(),calculated.cash(),calculated.equityColumns(),calculated.equity(),calculated.checks());
        var selectedLine=snapshot.position().stream().filter(r->r.label().equals(group)).findFirst().orElseThrow();
        var statements=new com.sacco.mvp.accounting.statements.service.StatementService(null,null,null,null,null,null,null);
        var selected=statements.selected(snapshot,com.sacco.mvp.accounting.statements.dto.StatementModels.Kind.POSITION,selectedLine.key());
        assertThat(selected).extracting(com.sacco.mvp.accounting.statements.dto.StatementModels.Balance::id).containsExactlyInAnyOrder(principal,secondPrincipal,allowance);
        assertThat(selected.stream().map(com.sacco.mvp.accounting.statements.dto.StatementModels.Balance::closing).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(selectedLine.current());
        var messages=new org.springframework.context.support.ResourceBundleMessageSource();messages.setBasename("messages");
        var presentation=new com.sacco.mvp.accounting.statements.service.StatementPresentation(messages);
        var trial=presentation.document(snapshot,com.sacco.mvp.accounting.statements.dto.StatementModels.Kind.TRIAL,selected,Locale.ENGLISH);
        assertThat(trial.rows()).filteredOn(r->r.details()!=null).hasSize(3);
        for(var balance:selected){
            var activity=repository.activity(run,balance.id(),period,0,5001);
            String expected=service.detail(actor,balance.id().equals(secondPrincipal)?second:first).loan().number();
            assertThat(activity).isNotEmpty().allSatisfy(row->assertThat(row.accountNumber()).isEqualTo(expected));
            assertThat(activity.stream().map(com.sacco.mvp.accounting.statements.dto.StatementModels.Activity::debit).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(balance.debit());
            assertThat(activity.stream().map(com.sacco.mvp.accounting.statements.dto.StatementModels.Activity::credit).reduce(BigDecimal.ZERO,BigDecimal::add)).isEqualByComparingTo(balance.credit());
            assertThat(trial.rows()).anySatisfy(row->assertThat(row.details()).isEqualTo("/reports/financial/statements/"+run+"/ledger/"+balance.id()));
        }
    }
    @Test void statementTopUpListsBothLoanIdsWithoutDuplicatingJournalAmounts(){
        var source=topUpSource();var loan=recordTopUp(source);var posting=service.disburse(actor,topUpPost(loan));
        var numbers=java.util.stream.Stream.of(service.detail(actor,source).loan().number(),service.detail(actor,loan).loan().number()).sorted().toList();
        var rows=statementActivity(principal).stream().filter(r->r.journalId().equals(posting.journalId())).toList();
        assertThat(rows).hasSize(1);assertThat(rows.getFirst().accountNumber()).isEqualTo(String.join(", ",numbers));
        assertThat(rows.getFirst().debit()).isEqualByComparingTo("1500.00");assertThat(rows.getFirst().credit()).isEqualByComparingTo("1000.00");
    }
    @Test void customisedFeesAreRecordedWithoutMoneyAndCollectedOnlyAtDisbursement(){
        product=jdbc.queryForObject("select id from loan_product_settings where sacco_id=? and custom_recording_configuration",UUID.class,institution);
        chargePolicy("DEDUCT",true,new BigDecimal("20.00"));
        var f=form();f.setLoanMode("CUSTOM");f.setProductId(null);f.setInterestPercent(new BigDecimal("12"));
        f.setCustomApplicationCharge(new BigDecimal("3.21"));f.setCustomProcessingCharge(new BigDecimal("6.78"));f.setInsurancePremium(new BigDecimal("7.65"));
        f.setInsuranceEvidence("Synthetic actual insurer premium / "+UUID.randomUUID());f.setEvidence("Synthetic signed customised agreement");
        var permissions=EnumSet.allOf(UserClaim.class);permissions.remove(UserClaim.LOAN_CHARGES_CUSTOM);
        var restricted=new AppUserPrincipal(context.getBean(MemberDirectoryService.class).find(actor.getMemberId()).orElseThrow(),permissions,true);
        assertThatThrownBy(()->service.save(restricted,f)).isInstanceOf(AccessDeniedException.class);
        var id=service.save(actor,f);assertThat(service.save(actor,f)).isEqualTo(id);
        var saved=service.chargeSummary(actor,id,0);
        assertThat(saved.plan().terms()).extracting(com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Term::amount)
            .containsExactly(new BigDecimal("3.21"),new BigDecimal("6.78"),new BigDecimal("7.65"));
        assertThat(saved.balance().assessed()).isZero();assertThat(saved.balance().collected()).isZero();assertThat(saved.history()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from accountant_loan_post where loan_id=?",Integer.class,id)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from loan_ledgers where loan_application_id=?",Integer.class,id)).isZero();
        jdbc.update("update loan_product_settings set application_fee=999,processing_fee_rate=0.5,insurance_rate=0.5 where id=?",product);
        assertThat(service.chargeSummary(actor,id,0).plan().total()).isEqualByComparingTo("17.64");
        var funding=chargedFunding(id);var posting=service.disburse(actor,funding);assertThat(service.disburse(actor,funding).id()).isEqualTo(posting.id());
        var funded=service.chargeSummary(actor,id,0);assertThat(funded.balance().collected()).isEqualByComparingTo("17.64");
        assertThat(funded.balance().outstanding()).isZero();
        assertThat(jdbc.queryForObject("select sum(credit-debit) from gl_journal_line where account_id=?",BigDecimal.class,cash)).isEqualByComparingTo("982.36");
    }
    @Test void chargesAreFrozenAssessedAndCollectedInterestThenFeesThenPrincipal(){
        chargePolicy("SEPARATE");var id=chargedLoan();assertThat(service.chargeSummary(actor,id,0).balance().assessed()).isZero();
        jdbc.update("update loan_product_settings set application_fee=999 where id=?",product);
        service.disburse(actor,chargedFunding(id));assertThat(service.chargeSummary(actor,id,0).balance().outstanding()).isEqualByComparingTo("30.00");
        assertThat(jdbc.queryForObject("select sum(credit-debit) from gl_journal_line where account_id=?",BigDecimal.class,insuranceAccount)).isEqualByComparingTo("10.00");
        var f=post(id,"25.00","CHARGE-R1");f.setInterestAccountId(interest);var first=service.repay(actor,f);assertThat(first.interest()).isEqualByComparingTo("20.00");assertThat(first.charges()).isEqualByComparingTo("5.00");assertThat(first.principal()).isZero();
        assertThat(service.repay(actor,f).id()).isEqualTo(first.id());
        var next=post(id,"30.00","CHARGE-R2");next.setInterestAccountId(interest);var second=service.repay(actor,next);assertThat(second.charges()).isEqualByComparingTo("25.00");assertThat(second.principal()).isEqualByComparingTo("5.00");
        var other=operator("B1");service.reverse(other,second.id(),UUID.randomUUID(),TODAY,"Synthetic incorrect cash receipt");assertThat(service.chargeSummary(actor,id,0).balance().outstanding()).isEqualByComparingTo("25.00");
        assertThat(jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where account_id=?",BigDecimal.class,chargeAccount)).isEqualByComparingTo("25.00");
    }
    @Test void deductionsReduceCashWithoutIncreasingContractPrincipal(){
        chargePolicy("DEDUCT");var id=chargedLoan();var p=service.disburse(actor,chargedFunding(id));
        assertThat(service.detail(actor,id).loan().principal()).isEqualByComparingTo("1000.00");assertThat(service.fundingCash(actor,id,TODAY)).isEqualByComparingTo("970.00");assertThat(service.chargeSummary(actor,id,0).balance().outstanding()).isZero();
        assertThat(jdbc.queryForObject("select sum(credit-debit) from gl_journal_line where journal_id=? and account_id=?",BigDecimal.class,p.journalId(),cash)).isEqualByComparingTo("970.00");
        assertThat(jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where account_id=?",BigDecimal.class,chargeAccount)).isZero();
    }
    @Test void chargeOnlyReceiptsAreAtomicConcurrentAndDoNotInventPrincipalPayments()throws Exception{
        chargePolicy("SEPARATE");var id=chargedLoan();service.disburse(actor,chargedFunding(id));var f=receiptForm(id);f.setEffectiveDate(LocalDate.of(2026,9,1));f.getRows().getFirst().setAmount(new BigDecimal("7.00"));
        try(var pool=Executors.newFixedThreadPool(2)){var start=new CountDownLatch(1);Callable<UUID> work=()->{start.await();return receiptService().post(actor,f).header().id();};var one=pool.submit(work);var two=pool.submit(work);start.countDown();assertThat(one.get()).isEqualTo(two.get());}
        var saved=receiptService().post(actor,f);assertThat(saved.rows().getFirst().charges()).isEqualByComparingTo("7.00");assertThat(saved.rows().getFirst().chargeAccount()).contains("CHARGES");
        assertThat(jdbc.queryForObject("select count(*) from loan_repayment_transactions where loan_application_id=?",Integer.class,id)).isZero();
        assertThat(service.chargeSummary(actor,id,0).balance().outstanding()).isEqualByComparingTo("23.00");
        var other=operator("B1");receiptService().reverse(other,saved.header().id(),UUID.randomUUID(),TODAY,"Synthetic returned collection");assertThat(service.chargeSummary(actor,id,0).balance().outstanding()).isEqualByComparingTo("30.00");
    }
    @Test void chargedFundingCorrectionReversesDeductionsAndAssessmentsTogether(){
        chargePolicy("DEDUCT");var id=chargedLoan();service.disburse(actor,chargedFunding(id));var request=adjustments().request(actor,adjustment(id,"DISBURSEMENT_REVERSAL"));var other=operator("B1");adjustments().decide(other,request,UUID.randomUUID(),true,"Synthetic verified cash return");
        assertThat(status(id)).isEqualTo("DISBURSEMENT_REVERSED");var balance=service.chargeSummary(actor,id,0).balance();assertThat(balance.assessed()).isZero();assertThat(balance.collected()).isZero();assertThat(balance.outstanding()).isZero();
        for(var account:List.of(cash,principal,chargeAccount,managementAccount,insuranceAccount))assertThat(jdbc.queryForObject("select coalesce(sum(debit-credit),0) from gl_journal_line where account_id=?",BigDecimal.class,account)).isZero();
        assertThatThrownBy(()->jdbc.update("delete from loan_charge_event where loan_id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void sourceChargesSettleOnceAndNewInsuranceDeductionIsIndependentlyDisclosed(){
        chargePolicy("DEDUCT");var source=chargedLoan();service.disburse(actor,chargedFunding(source));
        // Use an unpaid source for settlement, preserving the first loan's original terms.
        var p=chargeService().policy(actor,product).orElseThrow();var policy=new com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.PolicyForm();policy.setExpectedVersion(p.id());policy.setProductId(product);policy.setEffectiveFrom(LocalDate.of(2026,8,1));policy.setEnabled(true);policy.setPolicyReference("Synthetic separate collection policy");policy.setAgreementReference("Synthetic bilingual agreement");policy.setReceivableAccountId(chargeAccount);policy.setManagementAccountId(managementAccount);policy.setInsuranceAccountId(insuranceAccount);policy.setInsurer("Synthetic insurer");policy.setInsurerVerification("Synthetic licensed insurance evidence");chargeService().savePolicy(actor,policy);
        var original=chargedLoan();service.disburse(actor,chargedFunding(original));
        assertThatThrownBy(()->recordTopUp(original)).hasMessage("charges.error.topUpFee");
        jdbc.update("update loan_product_settings set application_fee=0,processing_fee_rate=0,insurance_rate=0.005 where id=?",product);
        var latest=chargeService().policy(actor,product).orElseThrow();policy.setRequestKey(UUID.randomUUID());policy.setExpectedVersion(latest.id());policy.setEffectiveFrom(LocalDate.of(2026,10,1));policy.setInsuranceTiming(com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Timing.DEDUCT);chargeService().savePolicy(actor,policy);
        var f=form();f.setTopUpSourceLoanId(original);f.setPrincipal(new BigDecimal("1500.00"));f.setApplicationDate(TODAY);f.setFirstPaymentDate(null);f.setInsurancePremium(new BigDecimal("7.50"));f.setInsuranceEvidence("Synthetic actual new insurer premium");f.setEvidence("Synthetic signed replacement agreement");var id=service.save(actor,f);var funding=topUpPost(id);var quote=service.disbursementTopUp(actor,id,TODAY).orElseThrow();funding.setSettlementCharges(quote.charges());funding.setSettlementChargeSequence(quote.chargeSequence());service.disburse(actor,funding);
        var topup=service.topUp(actor,id).orElseThrow();assertThat(topup.sourceCharges()).isEqualByComparingTo("30.00");assertThat(topup.newDeductions()).isEqualByComparingTo("7.50");assertThat(topup.cash()).isEqualByComparingTo("442.50");assertThat(service.chargeSummary(actor,original,0).balance().outstanding()).isZero();assertThat(service.chargeSummary(actor,id,0).balance().outstanding()).isZero();
        service.reverseTopUp(operator("B1"),id,UUID.randomUUID(),TODAY,"Synthetic returned transfer","Synthetic correction evidence");assertThat(service.chargeSummary(actor,original,0).balance().outstanding()).isEqualByComparingTo("30.00");assertThat(service.chargeSummary(actor,id,0).balance().assessed()).isZero();
    }
    @Test void assessmentRequiresEvidenceAndCorrectionsAreIndependentWithScopeAndClosedPeriodProtection(){
        chargePolicy("SEPARATE");var id=chargedLoan();jdbc.update("insert into accounting_period(id,sacco_id,starts_on,ends_on,state,created_by,created_at) values(?,?,'2026-09-01','2026-09-30','OPEN',?,now()),(?,?,'2026-10-01','2026-10-31','CLOSED',?,now())",UUID.randomUUID(),institution,actor.getMemberId(),UUID.randomUUID(),institution,actor.getMemberId());var f=new com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.AssessmentForm();f.setDate(LocalDate.of(2026,9,1));
        assertThatThrownBy(()->chargeService().assess(actor,id,f)).hasMessage("charges.error.evidence");f.setEvidence("Synthetic signed agreement");var command=chargeService().assess(actor,id,f);assertThat(chargeService().assess(actor,id,f)).isEqualTo(command);var summary=service.chargeSummary(actor,id,0);
        assertThatThrownBy(()->chargeService().reverseAssessment(actor,id,summary.assessmentJournal(),UUID.randomUUID(),TODAY,"Synthetic evidence","Correct terms")).hasMessage("charges.error.independent");
        assertThatThrownBy(()->service.chargeSummary(operator("B2"),id,0)).isInstanceOf(AccessDeniedException.class);
        var borrower=Member.builder().id(client).saccoId(institution).stationId("B1").memberAccount(true).build();var owner=new AppUserPrincipal(borrower,Set.of(UserClaim.MEMBER_LOANS_VIEW),false);assertThat(chargeService().clientSummary(owner,id,0).balance().outstanding()).isEqualByComparingTo("30.00");
        var stranger=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId(institution).stationId("B1").memberAccount(true).build(),Set.of(UserClaim.MEMBER_LOANS_VIEW),false);assertThatThrownBy(()->chargeService().clientSummary(stranger,id,0)).isInstanceOf(AccessDeniedException.class);
        var other=operator("B1");chargeService().reverseAssessment(other,id,summary.assessmentJournal(),UUID.randomUUID(),LocalDate.of(2026,9,1),"Synthetic replacement evidence","Correct assessment");assertThat(service.chargeSummary(actor,id,0).balance().assessed()).isZero();
        f.setDate(TODAY);f.setRequestKey(UUID.randomUUID());assertThatThrownBy(()->chargeService().assess(actor,id,f)).hasMessage("voucher.error.period");
    }
    @Test void chargeOnlyCollectionInvalidatesTopUpEvenWhenDebtSequenceDoesNotChange(){
        chargePolicy("SEPARATE");var source=chargedLoan();service.disburse(actor,chargedFunding(source));jdbc.update("update loan_product_settings set application_fee=0,processing_fee_rate=0,insurance_rate=0 where id=?",product);var id=recordTopUp(source);var funding=topUpPost(id);var quote=service.disbursementTopUp(actor,id,TODAY).orElseThrow();funding.setSettlementCharges(quote.charges());funding.setSettlementChargeSequence(quote.chargeSequence());
        var receipt=post(source,"7.00","FEE-ONLY");receipt.setEffectiveDate(LocalDate.of(2026,9,1));service.repay(actor,receipt);assertThat(service.disbursementTopUp(actor,id,TODAY).orElseThrow().sequence()).isEqualTo(quote.sequence());assertThatThrownBy(()->service.disburse(actor,funding)).hasMessage("recording.error.topUpChanged");assertThat(status(source)).isEqualTo("DISBURSED");
    }
    @Test void chargeAuditFailureAndInvalidMixedReceiptsLeaveNoPartialPostings(){
        chargePolicy("DEDUCT");var id=chargedLoan();doThrow(new IllegalStateException("Synthetic charge audit failure")).when(context.getBean(AuditService.class)).logEvent(eq("LOAN_RECORDING"),any(),eq("LOAN_DISBURSEMENT_POSTED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->service.disburse(actor,chargedFunding(id))).hasMessage("Synthetic charge audit failure");assertThat(status(id)).isEqualTo("READY_FOR_DISBURSEMENT");assertThat(service.chargeSummary(actor,id,0).balance().assessed()).isZero();assertThat(jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,institution)).isZero();reset(context.getBean(AuditService.class));
        service.disburse(actor,chargedFunding(id));var second=chargedLoan();service.disburse(actor,chargedFunding(second));var f=receiptForm(id,second);f.getRows().getLast().setAmount(new BigDecimal("99999.00"));assertThatThrownBy(()->receiptService().post(actor,f)).hasMessage("repayment.error.advance");assertThat(jdbc.queryForObject("select count(*) from accountant_loan_post where sacco_id=? and kind='PAYMENT'",Integer.class,institution)).isZero();
    }
    @Test void chargeOverridesRequirePermissionApprovedLimitAndDistinctInsuranceEvidence(){
        chargePolicy("SEPARATE");var latest=chargeService().policy(actor,product).orElseThrow();var p=new com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.PolicyForm();p.setProductId(product);p.setExpectedVersion(latest.id());p.setEffectiveFrom(LocalDate.of(2026,8,1));p.setEnabled(true);p.setPolicyReference("Synthetic approved custom charge limit");p.setAgreementReference("Synthetic approved agreement");p.setReceivableAccountId(chargeAccount);p.setManagementAccountId(managementAccount);p.setInsuranceAccountId(insuranceAccount);p.setInsurer(latest.insurer());p.setInsurerVerification(latest.insurerVerification());p.setAllowCustom(true);p.setCustomManagementLimit(new BigDecimal("10.00"));chargeService().savePolicy(actor,p);assertThat(chargeService().savePolicy(actor,p)).isNotNull();
        var f=form();f.setEvidence("Synthetic approved custom terms");f.setInsurancePremium(new BigDecimal("10.00"));f.setInsuranceEvidence("Synthetic unique premium "+UUID.randomUUID());f.setCustomApplicationCharge(new BigDecimal("5.00"));f.setCustomProcessingCharge(new BigDecimal("6.00"));assertThatThrownBy(()->service.save(actor,f)).hasMessage("charges.error.custom");f.setCustomProcessingCharge(new BigDecimal("5.00"));
        var permissions=EnumSet.allOf(UserClaim.class);permissions.remove(UserClaim.LOAN_CHARGES_CUSTOM);var member=context.getBean(MemberDirectoryService.class).find(actor.getMemberId()).orElseThrow();var restricted=new AppUserPrincipal(member,permissions,true);assertThatThrownBy(()->service.save(restricted,f)).isInstanceOf(AccessDeniedException.class);
        var id=service.save(actor,f);assertThat(service.chargeSummary(actor,id,0).plan().total()).isEqualByComparingTo("20.00");var duplicate=form();duplicate.setInsurancePremium(f.getInsurancePremium());duplicate.setInsuranceEvidence(f.getInsuranceEvidence());assertThatThrownBy(()->service.save(actor,duplicate)).hasMessage("charges.error.insuranceDuplicate");
        p.setRequestKey(UUID.randomUUID());p.setExpectedVersion(chargeService().policy(actor,product).orElseThrow().id());p.setEffectiveFrom(LocalDate.of(2026,10,1));p.setManagementTiming(com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Timing.FINANCED);assertThatThrownBy(()->chargeService().savePolicy(actor,p)).hasMessage("charges.error.financed");
    }
    @Test void chargeTermsAndOutstandingAmountsAppearInEnglishAndKiswahiliPdfDocuments()throws Exception{
        chargePolicy("SEPARATE");var id=chargedLoan();service.disburse(actor,chargedFunding(id));var payment=post(id,"25.00","PDF-CHARGES");payment.setInterestAccountId(interest);service.repay(actor,payment);
        var messages=new org.springframework.context.support.ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");var renderer=new com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService(new ReportExportLimiter(1));var controller=new com.sacco.mvp.accounting.loans.controller.LoanRecordingController(service,renderer,context.getBean(ApplicationClock.class),messages,mock(SaccoLogoStorageService.class));
        for(var language:List.of("en","sw")){
            var locale=Locale.forLanguageTag(language);var loanPdf=(org.springframework.http.ResponseEntity<?>)controller.loanExport(actor,id,new org.springframework.mock.web.MockHttpServletRequest("GET","/finance/loan-records/"+id+"/pdf"),locale,new org.springframework.ui.ExtendedModelMap());
            try(var document=org.apache.pdfbox.Loader.loadPDF((byte[])loanPdf.getBody())){var text=new org.apache.pdfbox.text.PDFTextStripper().getText(document);assertThat(text.toUpperCase(Locale.ROOT).replaceAll("\\s+"," ")).contains("25.00","10.00",language.equals("en")?"APPLICATION COMPONENT":"SEHEMU YA MAOMBI");if(language.equals("en"))javax.imageio.ImageIO.write(new org.apache.pdfbox.rendering.PDFRenderer(document).renderImageWithDPI(0,100),"png",java.nio.file.Path.of("target/loan-charges-verified.png").toFile());}
            var statement=(org.springframework.http.ResponseEntity<?>)controller.statementExport(actor,client,new org.springframework.mock.web.MockHttpServletRequest("GET","/finance/client-ledgers/"+client+"/pdf"),locale,new org.springframework.ui.ExtendedModelMap());try(var document=org.apache.pdfbox.Loader.loadPDF((byte[])statement.getBody())){assertThat(new org.apache.pdfbox.text.PDFTextStripper().getText(document).toUpperCase(Locale.ROOT).replaceAll("\\s+"," ")).contains("25.00",language.equals("en")?"OUTSTANDING CHARGES":"TOZO AMBAZO HAZIJALIPWA");}
        }
    }
    @Test void topUpSeparatesInternalSettlementFromCashAndExcludesFutureInterest(){
        var source=topUpSource();assertThat(service.detail(actor,source).loan().topUpEligible()).isTrue();
        var loan=recordTopUp(source);assertThat(service.detail(actor,source).loan().topUpEligible()).isFalse();var f=topUpPost(loan);var q=service.disbursementTopUp(actor,loan,TODAY).orElseThrow();
        assertThat(q.principal()).isEqualByComparingTo("1000.00");assertThat(q.interest()).isEqualByComparingTo("24.52");assertThat(q.excludedInterest()).isEqualByComparingTo("15.48");
        var p=service.disburse(actor,f);assertThat(service.disburse(actor,f).id()).isEqualTo(p.id());
        var topup=service.topUp(actor,loan).orElseThrow();assertThat(topup.cash()).isEqualByComparingTo("475.48");assertThat(status(source)).isEqualTo("PAID");
        assertThat(service.detail(actor,loan).loan().topUpEligible()).isFalse();
        assertThat(ledger.riskBalance(source,TODAY).orElseThrow().currentOutstanding()).isZero();assertThat(ledger.view(source,actor,0,0).futureInterest()).isZero();
        assertThat(service.detail(actor,source).loan().futureInterest()).isZero();assertThat(service.detail(actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("1500.00");
        assertThat(jdbc.queryForObject("select sum(credit-debit) from gl_journal_line where journal_id=? and account_id=?",BigDecimal.class,p.journalId(),cash)).isEqualByComparingTo("475.48");
        assertThat(jdbc.queryForObject("select count(*) from loan_repayment_transactions where loan_application_id=? and kind='PAYMENT'",Integer.class,source)).isZero();
        assertThat(service.history(actor,source,0).rows()).anySatisfy(row->assertThat(row.kind()).isEqualTo("SETTLEMENT"));
        assertThatThrownBy(()->service.topUpQuote(operator("B2"),source,TODAY,null)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void topUpRequiresFreshPreviewAndKeepsRecordedTermsFrozen(){
        var source=topUpSource();var loan=recordTopUp(source);var f=topUpPost(loan);
        var received=post(source,"120.00","SOURCE-PARTIAL");received.setInterestAccountId(interest);service.repay(actor,received);
        assertThatThrownBy(()->service.disburse(actor,f)).hasMessage("recording.error.topUpChanged");
        assertThat(status(source)).isEqualTo("DISBURSED");assertThat(service.topUp(actor,loan)).isEmpty();
        var refreshed=topUpPost(loan);service.disburse(actor,refreshed);assertThat(service.topUp(actor,loan).orElseThrow().cash()).isEqualByComparingTo("595.48");
        assertThat(service.detail(actor,loan).loan().principal()).isEqualByComparingTo("1500.00");
        var duplicate=form();duplicate.setTopUpSourceLoanId(source);duplicate.setApplicationDate(TODAY);duplicate.setPrincipal(new BigDecimal("2000.00"));duplicate.setEvidence("Synthetic agreement");
        assertThatThrownBy(()->service.save(actor,duplicate)).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void topUpCorrectionIsIndependentLinkedIdempotentAndRestoresSource(){
        var source=topUpSource();var loan=recordTopUp(source);service.disburse(actor,topUpPost(loan));var original=service.topUp(actor,loan).orElseThrow();
        assertThatThrownBy(()->service.reverseTopUp(actor,loan,UUID.randomUUID(),TODAY,"Correct transfer","Synthetic cash return proof")).hasMessage("recording.error.checker");
        var other=operator("B1");var key=UUID.randomUUID();var reversal=service.reverseTopUp(other,loan,key,TODAY,"Correct transfer","Synthetic cash return proof");
        assertThat(service.reverseTopUp(other,loan,key,TODAY,"Correct transfer","Synthetic cash return proof").id()).isEqualTo(reversal.id());
        assertThat(status(source)).isEqualTo("DISBURSED");assertThat(status(loan)).isEqualTo("DISBURSEMENT_REVERSED");
        assertThat(service.detail(actor,source).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");assertThat(service.detail(actor,source).loan().futureInterest()).isEqualByComparingTo("15.48");
        assertThat(service.detail(actor,loan).loan().outstandingPrincipal()).isZero();assertThat(service.topUp(actor,loan).orElseThrow().reversedBy()).isEqualTo(reversal.id());
        assertThat(jdbc.queryForObject("select (financial_snapshot->>'paymentSummaryTotalOutstanding')::numeric from loan_applications where id=?",BigDecimal.class,loan)).isZero();
        assertThat(jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where journal_id in (?,?)",BigDecimal.class,original.journalId(),reversal.journalId())).isZero();
        var reversals=new Filter("",null,null,"REVERSAL","newest",0,null);
        assertThat(service.register(actor,"DISBURSEMENT",reversals).rows()).anySatisfy(row->assertThat(row.kind()).isEqualTo("FUNDING_UNDO"));
        assertThat(service.totals(actor,"DISBURSEMENT",reversals).amount()).isEqualByComparingTo(original.cash().negate());
    }
    @Test void concurrentTopUpRetriesPostOnce()throws Exception{
        var source=topUpSource();var loan=recordTopUp(source);var f=topUpPost(loan);var start=new CountDownLatch(1);
        try(var pool=Executors.newFixedThreadPool(2)){
            var first=pool.submit(()->{start.await();return service.disburse(actor,f).id();});var second=pool.submit(()->{start.await();return service.disburse(actor,f).id();});start.countDown();
            assertThat(first.get(60,TimeUnit.SECONDS)).isEqualTo(second.get(60,TimeUnit.SECONDS));
        }
        assertThat(jdbc.queryForObject("select count(*) from accountant_loan_topup where loan_id=?",Integer.class,loan)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from loan_repayment_transactions where loan_application_id=? and kind='SETTLEMENT'",Integer.class,source)).isEqualTo(1);
    }
    @Test void topUpClosedPeriodCannotSettleSource(){
        var source=topUpSource();var loan=recordTopUp(source);var f=topUpPost(loan);
        jdbc.update("insert into accounting_period(id,sacco_id,starts_on,ends_on,state,created_by,created_at) values(?,?,'2026-10-01','2026-10-31','CLOSED',?,now())",UUID.randomUUID(),institution,actor.getMemberId());
        assertThatThrownBy(()->service.disburse(actor,f)).hasMessage("voucher.error.period");assertThat(status(source)).isEqualTo("DISBURSED");assertThat(service.topUp(actor,loan)).isEmpty();
    }
    @Test void topUpCorrectionRefusesDependentPaymentsAndAuditFailureRollsEverythingBack(){
        var source=topUpSource();var loan=recordTopUp(source);var f=topUpPost(loan);var audit=context.getBean(AuditService.class);
        doThrow(new IllegalStateException("Synthetic audit failure")).when(audit).logEvent(eq("LOAN_REPAYMENT"),any(),eq("LOAN_REPAYMENT_SETTLEMENT"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->service.disburse(actor,f)).isInstanceOf(IllegalStateException.class);reset(audit);
        assertThat(status(source)).isEqualTo("DISBURSED");assertThat(service.topUp(actor,loan)).isEmpty();
        service.disburse(actor,f);
        // A zero-principal financial adjustment is still a dependency requiring its own resolution.
        var dependent=form();dependent.setTopUpSourceLoanId(loan);dependent.setApplicationDate(TODAY);dependent.setFirstPaymentDate(null);dependent.setPrincipal(new BigDecimal("2500.00"));dependent.setEvidence("Synthetic dependent signed agreement");service.save(actor,dependent);assertThatThrownBy(()->service.reverseTopUp(operator("B1"),loan,UUID.randomUUID(),TODAY,"Correct transfer","Synthetic proof")).hasMessage("recording.error.topUpDependencies");
    }
    com.sacco.mvp.accounting.adjustments.service.LoanAdjustmentService adjustments(){return context.getBean(com.sacco.mvp.accounting.adjustments.service.LoanAdjustmentService.class);}
    com.sacco.mvp.accounting.adjustments.dto.LoanAdjustmentDtos.Form adjustment(UUID loan,String kind){
        var f=new com.sacco.mvp.accounting.adjustments.dto.LoanAdjustmentDtos.Form();f.setLoanId(loan);f.setKind(com.sacco.mvp.accounting.adjustments.dto.LoanAdjustmentDtos.Kind.valueOf(kind));f.setDate(TODAY);f.setReason("Synthetic accounting review");f.setEvidence("Synthetic approved policy evidence");return f;
    }
    UUID provide(UUID loan,String amount){var f=adjustment(loan,"PROVISION");f.setTargetAllowance(new BigDecimal(amount));f.setAllowanceAccountId(account("ALLOW-"+UUID.randomUUID().toString().substring(0,8),"ASSET","CREDIT","POSTING","ALLOWANCE"));f.setExpenseAccountId(account("LOSS-"+UUID.randomUUID().toString().substring(0,8),"EXPENSE","DEBIT","POSTING","EXPENSE"));var id=adjustments().request(actor,f);adjustments().decide(operator("B1"),id,UUID.randomUUID(),true,"Synthetic independent review");return id;}
    @Test void loanAdjustmentsPostProvisionsWriteOffAndLinkedWriteBackWithoutInventingPayments(){
        var loan=disbursed("D-ADJ-LIFECYCLE");provide(loan,"1000.00");
        assertThat(adjustments().context(actor,loan).allowance()).isEqualByComparingTo("1000.00");
        var request=adjustments().request(actor,adjustment(loan,"WRITE_OFF"));var reviewer=operator("B1");var key=UUID.randomUUID();
        adjustments().decide(reviewer,request,key,true,"Approved synthetic write-off");
        assertThat(adjustments().decide(reviewer,request,key,true,"Approved synthetic write-off")).isEqualTo(request);
        assertThat(status(loan)).isEqualTo("WRITTEN_OFF");assertThat(adjustments().context(actor,loan).bookPrincipal()).isZero();
        assertThat(adjustments().context(actor,loan).outstanding()).isEqualByComparingTo("1000.00");
        assertThat(adjustments().context(actor,loan).allowance()).isZero();
        var reports=new com.sacco.mvp.accounting.reports.LoanPortfolioReportRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc));
        var scope=new com.sacco.mvp.accounting.reports.LoanPortfolioReportService.Scope(institution,"B1",false);
        var range=new com.sacco.mvp.accounting.reports.LoanPortfolioReportService.Parameters(LocalDate.of(2026,9,1),TODAY,OffsetDateTime.parse("2026-10-08T12:00:01+03:00"),0);
        assertThat(reports.summary(scope,range).principal()).isZero();assertThat(reports.summary(scope,range).excluded()).isZero();
        assertThat(reports.adjustments(scope,range).get("writeOff")).isEqualByComparingTo("1000.00");
        assertThat(jdbc.queryForObject("select principal_paid from loan_ledgers where loan_application_id=?",BigDecimal.class,loan)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from loan_repayment_transactions where loan_application_id=?",Integer.class,loan)).isZero();
        assertThatThrownBy(()->service.repay(actor,post(loan,"10.00","NO-ORDINARY-PAYMENT"))).hasMessage("repayment.error.state");
        var back=adjustments().request(reviewer,adjustment(loan,"WRITE_BACK"));adjustments().decide(actor,back,UUID.randomUUID(),true,"Reinstate before actual recovery");
        assertThat(status(loan)).isEqualTo("DEFAULTED");assertThat(adjustments().context(actor,loan).bookPrincipal()).isEqualByComparingTo("1000.00");
        assertThat(adjustments().view(actor,back).reversesJournalId()).isEqualTo(adjustments().view(actor,request).journalId());
        var receipt=post(loan,"120.00","ACTUAL-RECOVERY");receipt.setInterestAccountId(interest);service.repay(actor,receipt);
        assertThat(adjustments().context(actor,loan).outstanding()).isEqualByComparingTo("900.00");
        assertThat(reports.summary(scope,range).principal()).isEqualByComparingTo("900.00");
        assertThat(reports.adjustments(scope,range).get("writeOff")).isZero();
    }
    @Test void provisionTargetsUseDeltasAndCannotMoveAllowanceToAnotherAccount(){
        var loan=disbursed("D-ADJ-TARGET");var original=provide(loan,"100.00");var q=adjustments().view(actor,original);
        var backdated=post(loan,"10.00","ADJ-BEFORE-ASSESSMENT");backdated.setInterestAccountId(interest);backdated.setEffectiveDate(LocalDate.of(2026,10,1));
        assertThatThrownBy(()->service.repay(actor,backdated)).isInstanceOf(IllegalArgumentException.class).hasMessage("repayment.error.adjustmentDate");
        var release=adjustment(loan,"PROVISION");release.setTargetAllowance(BigDecimal.ZERO);release.setAllowanceAccountId(q.creditId());release.setExpenseAccountId(q.debitId());
        var id=adjustments().request(actor,release);assertThat(adjustments().view(actor,id).allowanceChange()).isEqualByComparingTo("-100.00");
        adjustments().decide(operator("B1"),id,UUID.randomUUID(),true,"Release assessed allowance");assertThat(adjustments().context(actor,loan).allowance()).isZero();
        var invalid=adjustment(loan,"WRITE_OFF");assertThatThrownBy(()->adjustments().request(actor,invalid)).hasMessage("adjustment.error.fullyProvided");
    }
    @Test void adjustmentDecisionsCheckIndependentReviewScopeFreshClaimsAndChangedBalances(){
        var loan=disbursed("D-ADJ-SECURITY");provide(loan,"1000.00");var f=adjustment(loan,"WRITE_OFF");var id=adjustments().request(actor,f);
        assertThat(adjustments().request(actor,f)).isEqualTo(id);f.setReason("Changed payload");assertThatThrownBy(()->adjustments().request(actor,f)).hasMessage("adjustment.error.retry");
        assertThatThrownBy(()->adjustments().decide(actor,id,UUID.randomUUID(),true,"Self review")).hasMessage("adjustment.error.independent");
        var otherBranch=operator("B2");assertThatThrownBy(()->adjustments().view(otherBranch,id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->adjustments().decide(otherBranch,id,UUID.randomUUID(),true,"Foreign review")).isInstanceOf(AccessDeniedException.class);
        var receipt=post(loan,"120.00","ADJ-CHANGED");receipt.setInterestAccountId(interest);service.repay(actor,receipt);
        var reviewer=operator("B1");assertThatThrownBy(()->adjustments().decide(reviewer,id,UUID.randomUUID(),true,"Stale review")).hasMessage("adjustment.error.changed");
        adjustments().decide(reviewer,id,UUID.randomUUID(),false,"Reject stale assessment");assertThat(status(loan)).isEqualTo("DISBURSED");
        when(context.getBean(UserClaimService.class).effectiveClaims(eq(reviewer.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of());
        assertThatThrownBy(()->adjustments().view(reviewer,id)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void disbursementReversalOffsetsOriginalAccountsAndRetainsContractHistory(){
        var loan=disbursed("D-ADJ-DISBURSEMENT");var f=adjustment(loan,"DISBURSEMENT_REVERSAL");
        var reviewer=operator("B1");var id=adjustments().request(actor,f);
        assertThatThrownBy(()->adjustments().decide(actor,id,UUID.randomUUID(),true,"Original teller review")).hasMessage("adjustment.error.independent");
        adjustments().decide(reviewer,id,UUID.randomUUID(),true,"Confirmed no client funds remain");
        assertThat(status(loan)).isEqualTo("DISBURSEMENT_REVERSED");assertThat(adjustments().context(actor,loan).bookPrincipal()).isZero();
        assertThat(service.detail(actor,loan).loan().outstandingPrincipal()).isZero();assertThat(ledger.view(loan,actor,0,0).outstandingPrincipal()).isZero();
        assertThat(ledger.list(actor,null,0).getContent()).allSatisfy(row->assertThat(row.outstandingPrincipal()).isZero());
        assertThat(ledger.refreshProjection(loan)).isFalse();
        assertThat(jdbc.queryForObject("select count(*) from loan_ledger_installments where loan_application_id=?",Integer.class,loan)).isPositive();
        assertThat(jdbc.queryForObject("select sum(z.debit-z.credit) from gl_journal_line z join gl_journal j on j.id=z.journal_id where j.sacco_id=? and z.account_id=?",BigDecimal.class,institution,principal)).isZero();
        assertThatThrownBy(()->service.disburse(actor,post(loan,"1000.00","REDISBURSE"))).hasMessage("recording.error.state");
    }
    @Test void adjustmentsRejectRepaymentHistoryTamperingAndRollbackAuditFailures(){
        var loan=disbursed("D-ADJ-GUARDS");var receipt=post(loan,"120.00","ADJ-RECEIPT");receipt.setInterestAccountId(interest);var paid=service.repay(actor,receipt);
        var requester=operator("B1");service.reverse(requester,paid.id(),UUID.randomUUID(),TODAY,"Correction");
        assertThatThrownBy(()->adjustments().request(requester,adjustment(loan,"DISBURSEMENT_REVERSAL"))).hasMessage("adjustment.error.repayments");
        provide(loan,"1000.00");var request=adjustments().request(actor,adjustment(loan,"WRITE_OFF"));
        assertThatThrownBy(()->jdbc.update("update loan_adjustment_request set amount=1 where id=?",request)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("update loan_applications set status='WRITTEN_OFF' where id=?",loan)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        doThrow(new IllegalStateException("Synthetic adjustment audit failure")).when(context.getBean(AuditService.class)).logEvent(eq("ACCOUNTING_LOAN_ADJUSTMENT"),eq(request),eq("LOAN_ADJUSTMENT_APPROVED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->adjustments().decide(requester,request,UUID.randomUUID(),true,"Audit rollback")).hasMessage("Synthetic adjustment audit failure");
        assertThat(status(loan)).isEqualTo("DISBURSED");assertThat(adjustments().view(actor,request).state()).isEqualTo("PENDING");
        reset(context.getBean(AuditService.class));
    }
    @Test void adjustmentsRejectPostingIntoAnExistingClosedPeriod(){
        jdbc.update("insert into accounting_period(id,sacco_id,starts_on,ends_on,state,created_by,created_at) values(?,?,'2026-09-01','2026-09-30','OPEN',?,now()),(?,?,'2026-10-01','2026-10-31','CLOSED',?,now())",UUID.randomUUID(),institution,actor.getMemberId(),UUID.randomUUID(),institution,actor.getMemberId());
        var loan=disbursed("D-ADJ-CLOSED");var request=adjustments().request(actor,adjustment(loan,"DISBURSEMENT_REVERSAL"));
        assertThatThrownBy(()->adjustments().decide(operator("B1"),request,UUID.randomUUID(),true,"Closed period")).isInstanceOf(IllegalArgumentException.class).hasMessage("voucher.error.period");
        assertThat(adjustments().view(actor,request).state()).isEqualTo("PENDING");assertThat(status(loan)).isEqualTo("DISBURSED");
    }
    @Test void concurrentAdjustmentApprovalPostsExactlyOneJournal()throws Exception{
        var loan=disbursed("D-ADJ-RACE");provide(loan,"1000.00");var id=adjustments().request(actor,adjustment(loan,"WRITE_OFF"));var reviewer=operator("B1");var key=UUID.randomUUID();var pool=Executors.newFixedThreadPool(2);
        try{var first=pool.submit(()->adjustments().decide(reviewer,id,key,true,"Concurrent identical review"));var second=pool.submit(()->adjustments().decide(reviewer,id,key,true,"Concurrent identical review"));assertThat(first.get(60,TimeUnit.SECONDS)).isEqualTo(second.get(60,TimeUnit.SECONDS));}finally{pool.shutdownNow();}
        assertThat(jdbc.queryForObject("select count(*) from gl_journal where source_type='LOAN_ADJUSTMENT' and source_reference=?",Integer.class,id.toString())).isEqualTo(1);
    }
    @Test void writeOffUsesRemainingPrincipalAfterPartialCollectionAndBackdatingCannotInvalidateAssessment(){
        var loan=disbursed("D-ADJ-PARTIAL");var payment=post(loan,"120.00","ADJ-PARTIAL");payment.setInterestAccountId(interest);service.repay(actor,payment);
        provide(loan,"900.00");var id=adjustments().request(actor,adjustment(loan,"WRITE_OFF"));adjustments().decide(operator("B1"),id,UUID.randomUUID(),true,"Reviewed remaining principal");
        assertThat(adjustments().view(actor,id).amount()).isEqualByComparingTo("900.00");
        assertThat(jdbc.queryForObject("select principal_paid from loan_ledgers where loan_application_id=?",BigDecimal.class,loan)).isEqualByComparingTo("100.00");
        var lastPoster=adjustments().view(actor,id).decidedBy();
        var originalReviewer=new AppUserPrincipal(context.getBean(MemberDirectoryService.class).find(lastPoster).orElseThrow(),EnumSet.allOf(UserClaim.class),true);
        var back=adjustments().request(originalReviewer,adjustment(loan,"WRITE_BACK"));adjustments().decide(actor,back,UUID.randomUUID(),true,"Reinstate remaining debt");
        var dated=post(loan,"10.00","ADJ-BACKDATED");dated.setInterestAccountId(interest);dated.setEffectiveDate(TODAY.minusDays(1));
        assertThatThrownBy(()->service.repay(actor,dated)).isInstanceOfAny(IllegalArgumentException.class,org.springframework.dao.DataAccessException.class);
        assertThat(adjustments().context(actor,loan).outstanding()).isEqualByComparingTo("900.00");
    }
    @Test void configuredProductChoicesIncludeExactAmountLimitsAndResolvedTenureLimits(){
        jdbc.update("update loan_product_settings set tenure_unit='DAYS',minimum_amount=1000,maximum_amount=9007199254740991.01,min_tenure=7,max_tenure=30 where id=?",product);
        var choice=service.choices(actor,"products","",0).stream().filter(c->c.id().equals(product)).findFirst().orElseThrow();
        assertThat(choice.tenureUnit()).isEqualTo("DAYS");
        assertThat(choice.minimumAmount()).isEqualTo("1000.00");assertThat(choice.maximumAmount()).isEqualTo("9007199254740991.01");
        assertThat(choice.minimumTenure()).isEqualTo(7);assertThat(choice.maximumTenure()).isEqualTo(30);
        assertThat(service.filterProduct(actor,product)).isEqualTo(choice);
        assertThat(service.recordProduct(actor,product)).isEqualTo(choice);
        jdbc.update("update loan_product_settings set tenure_unit=null,min_tenure=null,max_tenure=null,min_repayment_months=2,max_repayment_months=18 where id=?",product);
        var legacy=service.choices(actor,"products","",0).stream().filter(c->c.id().equals(product)).findFirst().orElseThrow();
        assertThat(legacy.tenureUnit()).isEqualTo("MONTHS");assertThat(legacy.minimumTenure()).isEqualTo(2);assertThat(legacy.maximumTenure()).isEqualTo(18);
        jdbc.update("update loan_product_settings set min_repayment_months=0,max_repayment_months=null,minimum_amount=null,maximum_amount=null where id=?",product);
        var defaults=service.filterProduct(actor,product);
        assertThat(defaults.minimumTenure()).isEqualTo(1);assertThat(defaults.maximumTenure()).isEqualTo(600);
        assertThat(defaults.minimumAmount()).isNull();assertThat(defaults.maximumAmount()).isNull();
    }
    @Test void pagedScheduleBalancesCarryForwardPrincipalAndInterestAcrossPages(){
        var f=form();f.setLoanMode("CUSTOM");f.setCustomApplicationCharge(BigDecimal.ZERO);f.setCustomProcessingCharge(BigDecimal.ZERO);f.setInsurancePremium(BigDecimal.ZERO);f.setProductId(null);f.setTenureUnit("MONTHS");f.setTenureValue(12);
        f.setCustomFrequency(RepaymentFrequency.WEEKLY);f.setInterestPercent(new BigDecimal("12"));
        var id=service.save(actor,f);service.disburse(actor,post(id,"1000.00","D-SCHEDULE-BALANCES"));
        var complete=service.detail(actor,id).schedule();
        var expectedTotal=complete.stream().map(row->row.principal().add(row.interest())).reduce(BigDecimal.ZERO,BigDecimal::add);
        assertThat(expectedTotal).isGreaterThan(new BigDecimal("1000.00"));
        assertThat(complete.getFirst().beginningBalance()).isEqualByComparingTo(expectedTotal);
        assertThat(complete).allSatisfy(row->assertThat(row.endingBalance()).isEqualByComparingTo(row.beginningBalance().subtract(row.principal()).subtract(row.interest())));
        var page=service.actualRepaymentSchedule(actor,id,1).orElseThrow();
        assertThat(page.rows()).hasSize(25);assertThat(page.hasNext()).isTrue();
        assertThat(page.rows().getFirst().number()).isEqualTo(26);
        assertThat(page.rows().getFirst().beginningBalance()).isEqualByComparingTo(complete.get(25).beginningBalance());
        assertThat(page.rows().getFirst().endingBalance()).isEqualByComparingTo(complete.get(25).endingBalance());
        var repaymentPage=ledger.view(id,actor,0,1).installments().getContent();
        assertThat(repaymentPage.getFirst().beginningBalance()).isEqualByComparingTo(page.rows().getFirst().beginningBalance());
        assertThat(repaymentPage.getFirst().getScheduledPayment()).isEqualByComparingTo(page.rows().getFirst().amount());
        assertThat(repaymentPage.getFirst().endingBalance()).isEqualByComparingTo(page.rows().getFirst().endingBalance());
        assertThat(service.actualRepaymentSchedule(actor,id,2).orElseThrow().rows().getLast().endingBalance()).isZero();
        assertThatThrownBy(()->service.actualRepaymentSchedule(operator("B2"),id,0)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void referenceReportsReconcileGroupedReceiptsCorrectionsAndPortfolioBalances(){
        var first=disbursed("D-REFERENCE-1");var second=disbursed("D-REFERENCE-2");
        var receipt=receiptService().post(actor,receiptForm(first,second));
        var reports=new com.sacco.mvp.accounting.reports.ManagementReportRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc));
        var p=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(TODAY.minusMonths(1).withDayOfMonth(1),TODAY,OffsetDateTime.now().plusSeconds(1),null,null,"","",0);
        var daily=reports.read(actor,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.DAILY_COLLECTIONS,p,"","",false);
        assertThat(daily.count()).isEqualTo(4);assertThat(daily.totals().get("credit")).isEqualByComparingTo("240.00");assertThat(daily.excluded()).isZero();assertThat(daily.rows()).extracting(r->r.get("component")).containsOnly("PRINCIPAL","INTEREST");
        var portfolio=reports.read(actor,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.LOAN_PORTFOLIO,p,"","",false);
        assertThat(portfolio.count()).isEqualTo(2);assertThat(portfolio.excluded()).isZero();assertThat(portfolio.totals().get("principalBalance")).isEqualByComparingTo("1800.00");
        var risk=reports.read(actor,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.PORTFOLIO_RISK,p,"","",false);assertThat(risk.totals().get("band0")).isEqualByComparingTo("1800.00");
        var analysis=reports.read(actor,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.PORTFOLIO_ANALYSIS,p,"","",false);assertThat(analysis.totals().get("principalPaid")).isEqualByComparingTo("200.00");assertThat(analysis.totals().get("interestPaid")).isEqualByComparingTo("40.00");
        var cashbook=reports.read(actor,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.CASHBOOK,p,"","",false);assertThat(cashbook.totals().get("moneyIn")).isEqualByComparingTo("240.00");assertThat(cashbook.totals().get("moneyOut")).isEqualByComparingTo("2000.00");
        assertThat(reports.read(operator("B2"),com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.DAILY_COLLECTIONS,p,"","",false).count()).isZero();
        receiptService().reverse(operator("B1"),receipt.header().id(),UUID.randomUUID(),TODAY,"Reference report correction");
        var corrected=reports.read(actor,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.DAILY_COLLECTIONS,p,"","",false);assertThat(corrected.count()).isEqualTo(8);assertThat(corrected.totals().get("debit")).isEqualByComparingTo("240.00");assertThat(corrected.totals().get("credit")).isEqualByComparingTo("240.00");
        assertThat(reports.read(actor,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.PORTFOLIO_ANALYSIS,p,"","",false).totals().get("principalBalance")).isEqualByComparingTo("2000.00");
        for(int n=0;n<26;n++){var extra=receiptForm(first);extra.getRows().getFirst().setAmount(BigDecimal.ONE);receiptService().post(actor,extra);}
        var all=reports.read(actor,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.DAILY_COLLECTIONS,p,"","",false);assertThat(all.count()).isGreaterThanOrEqualTo(34);assertThat(all.rows()).hasSize(26);assertThat(all.totals().get("credit")).isEqualByComparingTo("266.00");
        var secondPage=new com.sacco.mvp.accounting.management.dto.ManagementReports.Parameters(p.from(),p.through(),p.cutoff(),null,null,"","",1);
        assertThat(reports.read(actor,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.DAILY_COLLECTIONS,secondPage,"","",false).rows()).hasSize((int)Math.min(26,all.count()-25));
        assertThat(reports.read(actor,com.sacco.mvp.accounting.management.dto.ManagementReports.Kind.CASHBOOK,p,"ZZZZ","",false).count()).isZero();
    }
    @Test void accountBudgetsPreserveRevisionsScopeRetriesAndConcurrentCreation()throws Exception{
        var repo=new com.sacco.mvp.accounting.management.repository.BudgetRepository(jdbc);
        var budget=new com.sacco.mvp.accounting.management.service.BudgetService(repo,context.getBean(GeneralLedgerService.class),context.getBean(ApplicationClock.class),context.getBean(AuditService.class));
        var tx=new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        var f=new com.sacco.mvp.accounting.management.dto.ManagementReports.BudgetForm();f.setAccountId(cash);f.setYear(2027);f.setDate(TODAY);f.setAmounts(new ArrayList<>(Collections.nCopies(12,new BigDecimal("100.01"))));
        UUID id=tx.execute(s->budget.save(actor,f));UUID retry=tx.execute(s->budget.save(actor,f));assertThat(retry).isEqualTo(id);assertThat(budget.view(actor,id).total()).isEqualByComparingTo("1200.12");
        assertThatThrownBy(()->budget.view(operator("B2"),id)).hasMessage("management.error.budget");
        f.setRequestKey(UUID.randomUUID());f.setReplacesId(id);f.getAmounts().set(0,new BigDecimal("100.02"));UUID revised=tx.execute(s->budget.save(actor,f));
        assertThat(budget.view(actor,revised).revision()).isEqualTo(2);assertThat(budget.view(actor,id).total()).isEqualByComparingTo("1200.12");assertThat(budget.list(actor,2027,0,"code","asc").rows()).extracting(com.sacco.mvp.accounting.management.dto.ManagementReports.Budget::id).containsExactly(revised);
        f.setRequestKey(UUID.randomUUID());assertThatThrownBy(()->tx.execute(s->budget.save(actor,f))).hasMessage("management.error.revision");
        assertThatThrownBy(()->jdbc.update("update account_budget set budget_year=2028 where id=?",id)).hasMessageContaining("append-only");
        f.setReplacesId(null);f.setYear(2028);f.setRequestKey(UUID.randomUUID());try(var pool=Executors.newFixedThreadPool(2)){var calls=List.<Callable<UUID>>of(()->tx.execute(s->budget.save(actor,f)),()->tx.execute(s->budget.save(actor,f)));var results=pool.invokeAll(calls);assertThat(results.getFirst().get()).isEqualTo(results.getLast().get());}
        f.setAccountId(principal);assertThatThrownBy(()->tx.execute(s->budget.save(actor,f))).hasMessage("management.error.retry");
    }
    @Test void combinedReceiptColumnSortsExecuteOnPostedScopedSources(){
        var id=disbursed("D-SORT");
        var larger=receiptForm(id);larger.setReference("Z-SORT");var largeReceipt=receiptService().post(actor,larger);
        var smaller=receiptForm(id);smaller.setReference("A-SORT");smaller.getRows().getFirst().setAmount(new BigDecimal("40.00"));var smallReceipt=receiptService().post(actor,smaller);
        for(var column:List.of("number","date","party","reference","transactions","amount","status"))for(var direction:List.of("asc","desc")){
            var rows=receiptService().list(actor,new com.sacco.mvp.accounting.dto.VoucherDtos.Filter("SORT",null,null,"",column+"_"+direction,0,10)).rows();
            assertThat(rows).hasSize(2);
            if(column.equals("amount") || column.equals("reference"))assertThat(rows.getFirst().id()).isEqualTo(direction.equals("asc")?smallReceipt.header().id():largeReceipt.header().id());
        }
        assertThat(receiptService().list(operator("B2"),new com.sacco.mvp.accounting.dto.VoucherDtos.Filter("SORT",null,null,"","amount_asc",0,10)).rows()).isEmpty();
        var repository=new LoanRecordingRepository(jdbc);
        for(var sort:List.of("number_asc","number_desc","balance_asc","balance_desc"))assertThat(repository.loans(actor,new Filter("",null,null,"",sort,0,null,null),TODAY,10,0)).hasSize(1);
    }
    @Test void receiptBatchAccountLinkingIsScopedVersionedAndKeepsFrozenAccounts(){
        var loan=disbursed("D-BATCH-LINK");var batch=batches().create(actor,"Synthetic account linking");var item=UUID.randomUUID();
        var saved=batches().save(actor,batch,item,new com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.SaveItem(0,receiptForm(loan)));
        var command=new com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.LinkAccounts(saved.version());
        int journals=jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,institution);
        var nextInterest=account("NEXT-BATCH-INTEREST","INCOME","CREDIT","POSTING","INCOME");
        jdbc.update("update loan_product_settings set interest_gl_account_id=? where id=?",nextInterest,product);
        batches().linkAccounts(actor,batch,item,command);batches().linkAccounts(actor,batch,item,command);
        assertThat(service.linkedAccounts(actor,loan).orElseThrow().interestId()).isEqualTo(interest);
        assertThat(jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,institution)).isEqualTo(journals);
        assertThatThrownBy(()->batches().linkAccounts(operator("B2"),batch,item,command)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->batches().linkAccounts(operator("B1"),batch,item,command)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->batches().linkAccounts(actor,batch,item,new com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.LinkAccounts(saved.version()+1))).hasMessage("batch.error.stale");
        assertThat(batches().estimate(actor,batch,item).lines().getFirst().interestAccountId()).isEqualTo(interest);
    }
    @Test void unifiedReceiptPostsAllLoansOnceAndRetainsHistoricalReceipts(){
        var first=disbursed("D-GROUP-1");var second=disbursed("D-GROUP-2");var f=receiptForm(first,second);
        var preview=receiptService().preview(actor,f);assertThat(preview).hasSize(2);assertThat(preview.getFirst().principal()).isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForObject("select count(*) from loan_repayment_transactions where sacco_id=?",Integer.class,institution)).isZero();
        var reserved=context.getBean(com.sacco.mvp.accounting.service.VoucherService.class).reserveNumber(actor,com.sacco.mvp.accounting.dto.VoucherDtos.Type.RECEIPT,TODAY,f.getRequestKey());
        var saved=receiptService().post(actor,f);assertThat(saved.header().total()).isEqualByComparingTo("240.00");assertThat(saved.rows()).hasSize(2);
        assertThat(saved.header().number()).isEqualTo(reserved.number());
        assertThat(jdbc.queryForObject("select consumed_at is not null from accounting_voucher_number_reservation where request_key=?",Boolean.class,f.getRequestKey())).isTrue();
        assertThat(receiptService().post(actor,f).header().id()).isEqualTo(saved.header().id());
        assertThat(jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,institution)).isEqualTo(4);
        assertThat(jdbc.queryForObject("select count(*) from accounting_voucher where sacco_id=?",Integer.class,institution)).isZero();
        assertThat(service.detail(actor,first).loan().outstandingPrincipal()).isEqualByComparingTo("900.00");
        f.getRows().getFirst().setAmount(new BigDecimal("121.00"));assertThatThrownBy(()->receiptService().post(actor,f)).hasMessage("voucher.error.retry");
        var historical=post(first,"10.00","HISTORICAL");historical.setInterestAccountId(interest);var old=service.repay(actor,historical);
        assertThat(receiptService().find(actor,old.id()).orElseThrow().rows()).hasSize(1);
        assertThat(receiptService().list(actor,new com.sacco.mvp.accounting.dto.VoucherDtos.Filter("",null,null,"","newest",0)).rows()).hasSize(2);
        assertThat(service.receiptForPosting(actor,saved.rows().getFirst().postingId())).contains(saved.header().id());
    }
    @Test void unifiedReceiptRollsBackEveryLoanWhenAnyRowFailsAndRejectsForeignClients(){
        var first=disbursed("D-ROLLBACK-1");var second=disbursed("D-ROLLBACK-2");var f=receiptForm(first,second);f.getRows().get(1).setAmount(new BigDecimal("9999.00"));
        assertThatThrownBy(()->receiptService().post(actor,f)).hasMessage("repayment.error.advance");
        assertThat(service.detail(actor,first).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
        assertThat(jdbc.queryForObject("select count(*) from accountant_loan_receipt where sacco_id=?",Integer.class,institution)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from loan_repayment_transactions where sacco_id=?",Integer.class,institution)).isZero();
        var repeated=receiptForm(first,first);assertThatThrownBy(()->receiptService().post(actor,repeated)).hasMessage("voucher.error.loanRow");
        var foreign=receiptForm(first);foreign.setClientId(UUID.randomUUID());assertThatThrownBy(()->receiptService().post(actor,foreign)).hasMessage("recording.error.client");
        assertThat(receiptService().choices(operator("B2"),"loans",client,"",0)).isEmpty();
    }
    @Test void unifiedReceiptRequiresCompleteIndependentCorrectionAndPreservesAllocations(){
        var first=disbursed("D-REVERSE-1");var second=disbursed("D-REVERSE-2");var saved=receiptService().post(actor,receiptForm(first,second));
        assertThatThrownBy(()->receiptService().reverse(actor,saved.header().id(),UUID.randomUUID(),TODAY,"Correction")).hasMessage("voucher.error.independentCorrection");
        var correctionStaff=operator("B1");var correctionClaims=EnumSet.allOf(UserClaim.class);correctionClaims.remove(UserClaim.ACCOUNTING_BUSINESS_CREATE);
        var corrector=new AppUserPrincipal(context.getBean(MemberDirectoryService.class).find(correctionStaff.getMemberId()).orElseThrow(),correctionClaims,true);
        assertThatThrownBy(()->service.reverse(corrector,saved.rows().getFirst().postingId(),UUID.randomUUID(),TODAY,"Partial correction")).hasMessage("recording.error.receiptCorrection");
        var key=UUID.randomUUID();var reversed=receiptService().reverse(corrector,saved.header().id(),key,TODAY,"Correction");assertThat(reversed.header().reversesId()).isEqualTo(saved.header().id());assertThat(reversed.rows()).hasSize(2);
        assertThat(receiptService().reverse(corrector,saved.header().id(),key,TODAY,"Correction").header().id()).isEqualTo(reversed.header().id());
        assertThat(service.detail(actor,first).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");assertThat(service.detail(actor,second).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
        assertThat(receiptService().find(actor,saved.header().id()).orElseThrow().header().status()).isEqualTo("REVERSED");
    }
    @Test void unifiedReceiptConcurrentRetriesAndAuditFailureAreAtomic()throws Exception{
        var id=disbursed("D-CONCURRENT");var f=receiptForm(id);var pool=Executors.newFixedThreadPool(2);
        try{var a=pool.submit(()->receiptService().post(actor,f));var b=pool.submit(()->receiptService().post(actor,f));assertThat(a.get(60,TimeUnit.SECONDS).header().id()).isEqualTo(b.get(60,TimeUnit.SECONDS).header().id());}finally{pool.shutdownNow();}
        var failed=receiptForm(id);failed.setReference("AUDIT-FAIL");doThrow(new IllegalStateException("Synthetic audit failure")).when(context.getBean(AuditService.class)).logEvent(eq("ACCOUNTING"),any(),eq("LOAN_RECEIPT_POSTED"),any(),any(),any(),any(),any(),any(),any(),any());
        assertThatThrownBy(()->receiptService().post(actor,failed)).hasMessage("Synthetic audit failure");assertThat(service.detail(actor,id).loan().outstandingPrincipal()).isEqualByComparingTo("900.00");
        assertThat(jdbc.queryForObject("select count(*) from accountant_loan_receipt where sacco_id=?",Integer.class,institution)).isEqualTo(1);
    }
    @Test void unifiedReceiptScopeRevocationClosedPeriodsAndDuplicateReferencesFailClosed(){
        var id=disbursed("D-SECURITY");var f=receiptForm(id);var saved=receiptService().post(actor,f);
        assertThat(receiptService().find(operator("B2"),saved.header().id())).isEmpty();
        var duplicate=receiptForm(id);duplicate.setReference(f.getReference());
        assertThatThrownBy(()->receiptService().post(actor,duplicate)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(service.detail(actor,id).loan().outstandingPrincipal()).isEqualByComparingTo("900.00");
        var closedDate=LocalDate.of(2026,9,15);
        jdbc.update("insert into accounting_period(id,sacco_id,starts_on,ends_on,state,created_by,created_at) values(?,?,?,?,'CLOSED',?,now())",UUID.randomUUID(),institution,closedDate,closedDate,actor.getMemberId());
        var closedForm=receiptForm(id);closedForm.setEffectiveDate(closedDate);
        assertThatThrownBy(()->receiptService().post(actor,closedForm)).hasMessage("voucher.error.period");
        when(context.getBean(UserClaimService.class).effectiveClaims(eq(actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of());
        assertThatThrownBy(()->receiptService().find(actor,saved.header().id())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->receiptService().export(actor,saved.header().id())).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->receiptService().post(actor,receiptForm(id))).isInstanceOf(AccessDeniedException.class);
    }
    com.sacco.mvp.accounting.batch.service.ReceiptBatchService batches(){return context.getBean(com.sacco.mvp.accounting.batch.service.ReceiptBatchService.class);}
    void seedHistoricalPolicy(){jdbc.update("insert into repayment_accounting_policy(id,sacco_id,version,effective_from,allocation_order,interest_account_id,reason,request_key,created_by,created_at) values(?,?,1,?,'INTEREST_FIRST',?,'Historical test policy',?,?,?)",UUID.randomUUID(),institution,LocalDate.of(2026,9,1),interest,UUID.randomUUID(),actor.getMemberId(),context.getBean(ApplicationClock.class).now());}
    UUID batchItem(UUID batch,com.sacco.mvp.accounting.dto.VoucherDtos.Form form){UUID id=UUID.randomUUID();batches().save(actor,batch,id,new com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.SaveItem(0,form));return id;}
    void processBatch(UUID id,boolean post){var summary=batches().get(actor,id);batches().start(actor,id,summary.batch().version(),post);var runner=new com.sacco.mvp.accounting.batch.service.ReceiptBatchWorker(context.getBean(com.sacco.mvp.accounting.batch.service.ReceiptBatchWorkerService.class),new io.micrometer.core.instrument.simple.SimpleMeterRegistry());for(int n=0;n<30 && Set.of("VALIDATING","POSTING").contains(batches().get(actor,id).batch().state());n++)runner.tick();}
    @Test void batchWithoutSettingsRoutesMixedProductPrincipalAndInterestToTheirFrozenAccounts(){
        UUID first=disbursed("D-MAPPED-FIRST");
        UUID secondPrincipal=account("PRODUCT2-PRINCIPAL","ASSET","DEBIT","POSTING","LOAN_PRINCIPAL");
        UUID secondInterest=account("PRODUCT2-INTEREST","INCOME","CREDIT","POSTING","INCOME");
        UUID secondProduct=UUID.randomUUID();
        jdbc.update("insert into loan_product_settings select (jsonb_populate_record(null::loan_product_settings,to_jsonb(p)||jsonb_build_object('id',?::text,'product_code','SECOND-MAPPED','product_name','Second mapped product','principal_gl_account_id',?::text,'interest_gl_account_id',?::text))).* from loan_product_settings p where id=?",secondProduct,secondPrincipal,secondInterest,product);
        var secondForm=form();secondForm.setProductId(secondProduct);UUID second=service.save(actor,secondForm);var funding=post(second,"1000.00","D-MAPPED-SECOND");funding.setPrincipalAccountId(null);service.disburse(actor,funding);
        var batch=batches().create(actor,"Mixed product collections");var input=receiptForm(first,second);input.setInterestAccountId(null);batchItem(batch,input);processBatch(batch,false);
        var preview=batches().items(actor,batch,0,"").rows().getFirst().preview();
        assertThat(preview.lines()).extracting(com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.PlanLine::interestAccountId).containsExactly(interest,secondInterest);
        assertThat(preview.lines()).extracting(com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.PlanLine::principalAccountId).containsExactly(principal,secondPrincipal);
        assertThat(preview.lines()).extracting(com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.PlanLine::product).containsExactly("Synthetic business loan","Second mapped product");
        processBatch(batch,true);assertThat(batches().get(actor,batch).posted()).isEqualTo(1);
        var saved=batches().items(actor,batch,0,"").rows().getFirst();var receipt=receiptService().find(actor,saved.receiptId()).orElseThrow();
        for(int n=0;n<2;n++){var row=receipt.rows().get(n);UUID expectedPrincipal=n==0?principal:secondPrincipal,expectedInterest=n==0?interest:secondInterest;
            var posting=service.posting(actor,row.postingId());assertThat(jdbc.queryForObject("select credit from gl_journal_line where journal_id=? and account_id=?",BigDecimal.class,posting.journalId(),expectedPrincipal)).isEqualByComparingTo("100.00");
            assertThat(jdbc.queryForObject("select credit from gl_journal_line where journal_id=? and account_id=?",BigDecimal.class,posting.journalId(),expectedInterest)).isEqualByComparingTo("20.00");}
        var corrected=receiptService().reverse(operator("B1"),receipt.header().id(),UUID.randomUUID(),TODAY,"Correct mixed product receipt");
        assertThat(corrected.rows()).hasSize(2);assertThat(service.detail(actor,first).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");assertThat(service.detail(actor,second).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
    }
    com.sacco.mvp.accounting.loans.service.LoanProductAccountService productAccountService(){
        return new com.sacco.mvp.accounting.loans.service.LoanProductAccountService(new com.sacco.mvp.accounting.loans.repository.LoanProductAccountRepository(jdbc),context.getBean(ApplicationClock.class),context.getBean(AuditService.class),context.getBean(GeneralLedgerService.class));
    }
    void configureProductAccounts(UUID principalAccount,UUID interestAccount){
        new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx->{
            var selected=context.getBean(LoanProductSettingRepository.class).findById(product).orElseThrow();
            productAccountService().configure(selected,actor.getMemberId(),principalAccount,interestAccount);
        });
    }
    @Test void productAccountSearchIncludesOnlyPostingAccountsAcrossClassificationsWithScopedPagination(){
        var expected=new ArrayList<UUID>();
        String[] types={"ASSET","LIABILITY","EQUITY","EXPENSE","INCOME"};
        for(int n=0;n<28;n++)expected.add(account("MAPPING-"+String.format("%03d",n),types[n%5],"DEBIT","POSTING","OTHER"));
        UUID heading=account("MAPPING-GROUP","ASSET","DEBIT","HEADING","OTHER");
        UUID control=account("MAPPING-PRINCIPAL","ASSET","DEBIT","POSTING","LOAN_PRINCIPAL");expected.add(control);
        var service=productAccountService();
        for(String category:List.of("PRINCIPAL","INTEREST")){
            var first=service.choices(institution,category,"mapping",0,null);var last=service.choices(institution,category,"mapping",1,null);
            assertThat(first).extracting(Choice::id).containsExactlyElementsOf(expected.subList(0,26));
            assertThat(last).extracting(Choice::id).containsExactlyElementsOf(expected.subList(25,29));
            assertThat(service.choices(institution,category,"",0,heading)).isEmpty();
            assertThat(service.choices(institution,category,"",0,control)).extracting(Choice::id).containsExactly(control);
            assertThat(service.choices(institution,category,"mapping-001",0,expected.get(1))).extracting(Choice::id).containsExactly(expected.get(1));
            assertThat(service.choices(institution+"-foreign",category,"mapping",0,expected.get(1))).isEmpty();
            assertThat(service.choices(institution,category,"%' OR '1'='1",0,null)).isEmpty();
        }
    }
    @Test void productMappingsSaveAnyClassificationButRejectHeadingsInactiveAndForeignAccounts(){
        var repository=new com.sacco.mvp.accounting.loans.repository.LoanProductAccountRepository(jdbc);
        for(String type:List.of("ASSET","LIABILITY","EQUITY","EXPENSE","INCOME")){
            UUID first=account("ANY-"+type,type,"CREDIT","POSTING","OTHER"),second=account("OTHER-"+type,type,"DEBIT","POSTING","OTHER");
            configureProductAccounts(first,second);
            assertThat(jdbc.queryForMap("select principal_gl_account_id,interest_gl_account_id from loan_product_settings where id=?",product))
                .containsEntry("principal_gl_account_id",first).containsEntry("interest_gl_account_id",second);
            assertThat(repository.valid(institution+"-foreign",first)).isFalse();
        }
        UUID heading=account("HEADING","ASSET","DEBIT","HEADING","OTHER"),inactive=account("INACTIVE","EXPENSE","DEBIT","POSTING","OTHER");
        context.getBean(GeneralLedgerService.class).deactivateAccount(actor,inactive);
        UUID control=account("PRODUCT-PRINCIPAL","ASSET","DEBIT","POSTING","LOAN_PRINCIPAL");
        configureProductAccounts(control,interest);
        configureProductAccounts(interest,control);
        assertThatThrownBy(()->configureProductAccounts(heading,interest)).hasMessage("recording.error.productAccountsInvalid");
        assertThatThrownBy(()->configureProductAccounts(principal,inactive)).hasMessage("recording.error.productAccountsInvalid");
    }
    @Test void arbitraryProductAccountTypesSupportFundingReceiptsAndIndependentReversal(){
        principal=account("CUSTOM-PRINCIPAL","LIABILITY","CREDIT","POSTING","OTHER");interest=account("CUSTOM-INTEREST","EXPENSE","DEBIT","POSTING","OTHER");
        configureProductAccounts(principal,interest);
        UUID loan=disbursed("D-CUSTOM-ACCOUNTS");var input=receiptForm(loan);var receipt=receiptService().post(actor,input);
        assertThat(service.detail(actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("900.00");
        var posting=service.posting(actor,receipt.rows().getFirst().postingId());
        assertThat(jdbc.queryForObject("select credit from gl_journal_line where journal_id=? and account_id=?",BigDecimal.class,posting.journalId(),interest)).isEqualByComparingTo("20.00");
        assertThat(receiptService().post(actor,input).header().id()).isEqualTo(receipt.header().id());
        receiptService().reverse(operator("B1"),receipt.header().id(),UUID.randomUUID(),TODAY,"Correct custom-account receipt");
        assertThat(service.detail(actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
        assertThatThrownBy(()->service.postingPreview(operator("B2"),post(loan,"120.00","WRONG-BRANCH"),"PAYMENT")).isInstanceOf(AccessDeniedException.class);
    }
    @Test void arbitraryProductAccountTypesSupportChargedLoansAndBatchReceipts(){
        principal=account("CUSTOM-PRINCIPAL","EQUITY","CREDIT","POSTING","OTHER");interest=account("CUSTOM-INTEREST","ASSET","DEBIT","POSTING","INTEREST_RECEIVABLE");
        configureProductAccounts(principal,interest);chargePolicy("SEPARATE");
        UUID loan=chargedLoan();service.disburse(actor,chargedFunding(loan));
        var collected=receiptService().post(actor,receiptForm(loan));
        receiptService().reverse(operator("B1"),collected.header().id(),UUID.randomUUID(),TODAY,"Correct charged receipt");
        assertThat(service.chargeSummary(actor,loan,0).balance().outstanding()).isEqualByComparingTo("30.00");
        receiptService().post(actor,receiptForm(loan));
        assertThat(service.chargeSummary(actor,loan,0).balance().outstanding()).isZero();
        UUID batch=batches().create(actor,"Configured account types");batchItem(batch,receiptForm(loan));processBatch(batch,false);processBatch(batch,true);
        assertThat(batches().get(actor,batch).posted()).isEqualTo(1);
        assertThat(service.detail(actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("810.00");
        assertThat(service.chargeSummary(actor,loan,0).balance().outstanding()).isZero();
    }
    @Test void arbitraryTopUpAccountsRetainSourceInterestAndReverseExactly(){
        principal=account("SOURCE-PRINCIPAL","LIABILITY","CREDIT","POSTING","OTHER");interest=account("SOURCE-INTEREST","EXPENSE","DEBIT","POSTING","OTHER");
        configureProductAccounts(principal,interest);UUID source=topUpSource(),sourceInterest=interest;
        principal=account("NEXT-PRINCIPAL","EQUITY","CREDIT","POSTING","OTHER");interest=account("NEXT-INTEREST","ASSET","DEBIT","POSTING","OTHER");
        configureProductAccounts(principal,interest);UUID loan=recordTopUp(source);var input=topUpPost(loan);input.setInterestAccountId(sourceInterest);
        var posting=service.disburse(actor,input);var topUp=service.topUp(actor,loan).orElseThrow();
        assertThat(jdbc.queryForObject("select credit from gl_journal_line where journal_id=? and account_id=?",BigDecimal.class,posting.journalId(),sourceInterest)).isEqualByComparingTo(topUp.interest());
        var correction=service.reverseTopUp(operator("B1"),loan,UUID.randomUUID(),TODAY,"Correct configured-account transfer","Synthetic returned funds evidence");
        assertThat(jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where journal_id in (?,?)",BigDecimal.class,posting.journalId(),correction.journalId())).isZero();
        assertThat(status(source)).isEqualTo("DISBURSED");assertThat(status(loan)).isEqualTo("DISBURSEMENT_REVERSED");
    }
    @Test void productEditsApplyToLaterDisbursementsAndCannotRedirectExistingLoanPayments(){
        UUID loan=disbursed("D-FROZEN-ACCOUNTS");UUID nextPrincipal=account("NEXT-PRINCIPAL","ASSET","DEBIT","POSTING","LOAN_PRINCIPAL"),nextInterest=account("NEXT-INTEREST","INCOME","CREDIT","POSTING","INCOME");
        jdbc.update("update loan_product_settings set principal_gl_account_id=?,interest_gl_account_id=? where id=?",nextPrincipal,nextInterest,product);
        var input=post(loan,"120.00","FROZEN-PAYMENT");input.setPrincipalAccountId(null);input.setInterestAccountId(null);var paid=service.repay(actor,input);
        assertThat(jdbc.queryForObject("select interest_account_id from accountant_loan_post where id=?",UUID.class,paid.id())).isEqualTo(interest);
        assertThat(service.repay(actor,input).id()).isEqualTo(paid.id());
        var second=service.save(actor,form());var funding=post(second,"1000.00","D-NEXT-FUNDING");funding.setPrincipalAccountId(null);var funded=service.disburse(actor,funding);
        assertThat(jdbc.queryForObject("select principal_account_id from accountant_loan_post where id=?",UUID.class,funded.id())).isEqualTo(nextPrincipal);
        service.linkExistingProductAccounts(actor,loan);assertThat(service.linkedAccounts(actor,loan).orElseThrow().interestId()).isEqualTo(interest);
        assertThatThrownBy(()->jdbc.update("update loan_product_account_binding set interest_account_id=? where loan_id=?",nextInterest,loan)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("delete from loan_product_account_binding where loan_id=?",loan)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->service.linkExistingProductAccounts(operator("B2"),loan)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void missingProductMappingBlocksFundingAndForgedAccountOverridesCannotPost(){
        UUID loan=service.save(actor,form());jdbc.update("update loan_product_settings set principal_gl_account_id=null,interest_gl_account_id=null where id=?",product);
        assertThatThrownBy(()->service.disburse(actor,post(loan,"1000.00","D-UNMAPPED"))).hasMessage("recording.error.productAccountsMissing");
        assertThat(jdbc.queryForObject("select count(*) from accountant_loan_post where loan_id=?",Integer.class,loan)).isZero();
        jdbc.update("update loan_product_settings set principal_gl_account_id=?,interest_gl_account_id=? where id=?",principal,interest,product);service.disburse(actor,post(loan,"1000.00","D-MAPPED"));
        var payment=post(loan,"120.00","FORGED");payment.setInterestAccountId(cash);assertThatThrownBy(()->service.repay(actor,payment)).hasMessage("recording.error.account");
        assertThat(jdbc.queryForObject("select count(*) from accountant_loan_post where loan_id=? and kind='PAYMENT'",Integer.class,loan)).isZero();
    }
    @Test void inactiveFrozenAccountsFailBatchRevalidationWithoutReceivingMoney(){
        seedHistoricalPolicy();UUID loan=disbursed("D-INACTIVE-MAPPING");UUID batch=batches().create(actor,"Inactive account guard");batchItem(batch,receiptForm(loan));processBatch(batch,false);
        context.getBean(GeneralLedgerService.class).deactivateAccount(actor,interest);
        assertThat(jdbc.queryForObject("select active from gl_account where id=?",Boolean.class,interest)).isFalse();processBatch(batch,true);
        assertThat(batches().get(actor,batch).posted()).isZero();assertThat(batches().get(actor,batch).failed()).isEqualTo(1);
        assertThat(service.detail(actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
    }
    @Test void receiptBatchStagesRepeatedLoansAndPostsEachReceiptExactlyOnce(){
        seedHistoricalPolicy();var first=disbursed("D-BATCH-1");var second=disbursed("D-BATCH-2");var batch=batches().create(actor,"Daily collections");
        batchItem(batch,receiptForm(first,second));batchItem(batch,receiptForm(first));processBatch(batch,false);
        var validated=batches().items(actor,batch,0,"").rows();assertThat(validated).allMatch(i->i.state().equals("READY"));
        assertThat(validated.getFirst().preview().interest()).isEqualByComparingTo("40.00");assertThat(validated.getLast().preview().interest()).isZero();assertThat(validated.getLast().preview().principal()).isEqualByComparingTo("120.00");
        assertThat(jdbc.queryForObject("select count(*) from accountant_loan_receipt where sacco_id=?",Integer.class,institution)).isZero();
        processBatch(batch,true);var result=batches().get(actor,batch);assertThat(result.batch().state()).isEqualTo("COMPLETED");assertThat(result.posted()).isEqualTo(2);assertThat(result.postedTotal()).isEqualByComparingTo("360.00");
        var worker=context.getBean(com.sacco.mvp.accounting.batch.service.ReceiptBatchWorkerService.class);assertThat(worker.claim()).isEmpty();
        assertThat(jdbc.queryForObject("select count(*) from loan_repayment_transactions where sacco_id=? and allocation_policy_id is not null",Integer.class,institution)).isEqualTo(3);
        var saved=batches().items(actor,batch,0,"").rows().getFirst();assertThat(receiptService().find(actor,saved.receiptId()).orElseThrow().rows()).hasSize(2);
        assertThatThrownBy(()->jdbc.update("update receipt_batch_item set receipt_id=null where id=?",saved.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void statutoryPolicyIsAppliedByPreviewAndLedgerAndRetainedOnCorrection(){
        seedHistoricalPolicy();var loan=disbursed("D-PFIRST");var f=receiptForm(loan);var preview=receiptService().preview(actor,f).getFirst();assertThat(preview.principal()).isEqualByComparingTo("100.00");assertThat(preview.interest()).isEqualByComparingTo("20.00");
        assertThatThrownBy(()->jdbc.update("insert into repayment_accounting_policy(id,sacco_id,version,effective_from,allocation_order,interest_account_id,reason,request_key,created_by,created_at) select ?,sacco_id,version+1,effective_from+1,'PRINCIPAL_FIRST',interest_account_id,reason,?,created_by,created_at from repayment_accounting_policy where sacco_id=?",UUID.randomUUID(),UUID.randomUUID(),institution)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        var receipt=receiptService().post(actor,f);assertThat(receipt.rows().getFirst().principal()).isEqualByComparingTo("100.00");assertThat(receipt.rows().getFirst().interest()).isEqualByComparingTo("20.00");
        var correction=receiptService().reverse(operator("B1"),receipt.header().id(),UUID.randomUUID(),TODAY,"Synthetic correction");
        assertThat(correction.rows().getFirst().principal()).isEqualByComparingTo("100.00");
        assertThat(jdbc.queryForObject("select count(distinct allocation_policy_id) from loan_repayment_transactions where loan_application_id=?",Integer.class,loan)).isEqualTo(1);
    }
    @Test void staleBatchPreviewFailsWithoutPostingAndCanBeRevalidated(){
        seedHistoricalPolicy();var loan=disbursed("D-STALE-BATCH");var batch=batches().create(actor,"Stale preview");batchItem(batch,receiptForm(loan));processBatch(batch,false);
        receiptService().post(actor,receiptForm(loan));processBatch(batch,true);assertThat(batches().get(actor,batch).batch().state()).isEqualTo("PARTIAL");assertThat(batches().get(actor,batch).posted()).isZero();
        assertThat(batches().items(actor,batch,0,"").rows().getFirst().failureKey()).isEqualTo("batch.error.changed");processBatch(batch,false);processBatch(batch,true);assertThat(batches().get(actor,batch).posted()).isEqualTo(1);
    }
    UUID futureLoan(String reference){
        var form=form();form.setApplicationDate(TODAY);form.setFirstPaymentDate(null);var id=service.save(actor,form);
        var funding=post(id,"1000.00",reference);funding.setEffectiveDate(TODAY);funding.setFirstPaymentDate(TODAY.plusMonths(1));service.disburse(actor,funding);return id;
    }
    @Test void arbitraryEarlyReceiptAllocatesPrincipalAndReversalRestoresFutureInstalments(){
        var loan=futureLoan("D-FUTURE-RECEIPT");var form=receiptForm(loan);form.getRows().getFirst().setAmount(new BigDecimal("237.19"));
        var preview=receiptService().preview(actor,form).getFirst();assertThat(preview.principal()).isEqualByComparingTo("237.19");assertThat(preview.interest()).isZero();
        var receipt=receiptService().post(actor,form);assertThat(receipt.rows().getFirst().principalBalance()).isEqualByComparingTo("762.81");
        assertThat(receiptService().post(actor,form).header().id()).isEqualTo(receipt.header().id());
        assertThat(jdbc.queryForObject("select sum(interest_paid) from loan_ledger_installments where loan_application_id=?",BigDecimal.class,loan)).isZero();
        assertThat(jdbc.queryForObject("select sum(principal_paid) from loan_ledger_installments where loan_application_id=?",BigDecimal.class,loan)).isEqualByComparingTo("237.19");
        receiptService().reverse(operator("B1"),receipt.header().id(),UUID.randomUUID(),TODAY,"Synthetic correction of early principal");
        assertThat(service.detail(actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
        assertThat(jdbc.queryForObject("select sum(principal_paid) from loan_ledger_installments where loan_application_id=?",BigDecimal.class,loan)).isZero();
    }
    @Test void successiveBatchReceiptsCanPayFuturePrincipalWithoutNegativeDueBalances(){
        seedHistoricalPolicy();var loan=futureLoan("D-FUTURE-BATCH");var batch=batches().create(actor,"Synthetic early principal collections");
        for(String amount:List.of("237.19","400.00")){var form=receiptForm(loan);form.getRows().getFirst().setAmount(new BigDecimal(amount));batchItem(batch,form);}
        processBatch(batch,false);var items=batches().items(actor,batch,0,"").rows();assertThat(items).allMatch(i->i.state().equals("READY"));
        assertThat(items.getLast().preview().lines().getFirst().duePrincipal()).isZero();assertThat(items.getLast().preview().lines().getFirst().principalBalance()).isEqualByComparingTo("362.81");
        processBatch(batch,true);assertThat(batches().get(actor,batch).posted()).isEqualTo(2);
        assertThat(service.detail(actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("362.81");
        assertThat(jdbc.queryForObject("select sum(interest_amount) from loan_repayment_transactions where loan_application_id=?",BigDecimal.class,loan)).isZero();
        assertThat(jdbc.queryForObject("select sum(credit) from gl_journal_line where sacco_id=? and account_id=?",BigDecimal.class,institution,principal)).isEqualByComparingTo("637.19");
    }
    @Test void batchValidationRejectsCombinedReceiptsAboveRemainingDebtBeforePosting(){
        seedHistoricalPolicy();var loan=futureLoan("D-FUTURE-OVERPAY");var batch=batches().create(actor,"Synthetic overpayment guard");
        for(String amount:List.of("600.00","400.01")){var form=receiptForm(loan);form.getRows().getFirst().setAmount(new BigDecimal(amount));batchItem(batch,form);}
        processBatch(batch,false);assertThat(batches().get(actor,batch).ready()).isEqualTo(1);assertThat(batches().get(actor,batch).failed()).isEqualTo(1);
        assertThat(batches().items(actor,batch,0,"").rows().getLast().failureKey()).isEqualTo("repayment.error.advance");
        assertThat(service.detail(actor,loan).loan().outstandingPrincipal()).isEqualByComparingTo("1000.00");
        assertThat(jdbc.queryForObject("select count(*) from loan_repayment_transactions where loan_application_id=?",Integer.class,loan)).isZero();
    }
    @Test void fullEarlyReceiptExcludesFutureInterestAndIndependentReversalRestoresIt(){
        var loan=futureLoan("D-EARLY-FULL");var form=receiptForm(loan);form.getRows().getFirst().setAmount(new BigDecimal("1000.00"));
        var receipt=receiptService().post(actor,form);var detail=service.detail(actor,loan).loan();
        assertThat(detail.status()).isEqualTo("PAID");assertThat(detail.futureInterest()).isZero();assertThat(detail.interestPaid()).isZero();
        assertThat(jdbc.queryForObject("select sum(interest) from loan_ledger_installments where loan_application_id=?",BigDecimal.class,loan)).isEqualByComparingTo("20.00");
        assertThat(jdbc.queryForObject("select sum(interest_excluded) from loan_ledger_installments where loan_application_id=?",BigDecimal.class,loan)).isEqualByComparingTo("20.00");
        assertThat(jdbc.queryForObject("select future_interest_excluded from loan_repayment_transactions where loan_application_id=?",BigDecimal.class,loan)).isEqualByComparingTo("20.00");
        assertThat(ledger.riskBalance(loan,TODAY.plusYears(1)).orElseThrow().currentOutstanding()).isZero();
        var reports=new com.sacco.mvp.accounting.reports.LoanPortfolioReportRepository(new org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate(jdbc));
        var scope=new com.sacco.mvp.accounting.reports.LoanPortfolioReportService.Scope(institution,"B1",false);
        var later=new com.sacco.mvp.accounting.reports.LoanPortfolioReportService.Parameters(TODAY,TODAY.plusYears(1),OffsetDateTime.now(),0);
        assertThat(reports.rows(scope,later)).singleElement().satisfies(row->{assertThat(row.complete()).isTrue();assertThat(row.overdueInterest()).isZero();});
        assertThatThrownBy(()->jdbc.update("update loan_ledger_installments set interest_excluded=0 where loan_application_id=?",loan)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        receiptService().reverse(operator("B1"),receipt.header().id(),UUID.randomUUID(),TODAY,"Synthetic full early repayment correction");
        detail=service.detail(actor,loan).loan();assertThat(detail.status()).isEqualTo("DISBURSED");assertThat(detail.outstandingPrincipal()).isEqualByComparingTo("1000.00");
        assertThat(detail.futureInterest()).isEqualByComparingTo("20.00");assertThat(jdbc.queryForObject("select sum(interest_excluded) from loan_ledger_installments where loan_application_id=?",BigDecimal.class,loan)).isZero();
        assertThat(jdbc.queryForObject("select count(*) from loan_repayment_transactions where loan_application_id=? and future_interest_excluded=20",Integer.class,loan)).isEqualTo(2);
        assertThat(reports.rows(scope,later)).singleElement().satisfies(row->assertThat(row.overdueInterest()).isEqualByComparingTo("20.00"));
    }
    @Test void batchDoesNotPlanASecondCollectionOfInterestExcludedByAnEarlierFullRepayment(){
        seedHistoricalPolicy();var loan=disbursed("D-BATCH-FULL-EARLY");var batch=batches().create(actor,"Synthetic settlement cutoff");
        var early=receiptForm(loan);early.setEffectiveDate(LocalDate.of(2026,9,15));early.getRows().getFirst().setAmount(new BigDecimal("1000.00"));batchItem(batch,early);
        var later=receiptForm(loan);later.getRows().getFirst().setAmount(new BigDecimal("20.00"));batchItem(batch,later);
        processBatch(batch,false);assertThat(batches().get(actor,batch).ready()).isEqualTo(1);assertThat(batches().get(actor,batch).failed()).isEqualTo(1);
        assertThat(batches().items(actor,batch,0,"").rows().getLast().failureKey()).isEqualTo("repayment.error.advance");
    }
    @Test void batchReceiptAuditFailureRollsBackEveryTransactionAndRetryKeepsSuccessfulReceipts(){
        seedHistoricalPolicy();var first=disbursed("D-ATOMIC-1");var second=disbursed("D-ATOMIC-2");var batch=batches().create(actor,"Atomic receipts");batchItem(batch,receiptForm(first,second));processBatch(batch,false);
        doThrow(new IllegalStateException("Synthetic failure")).when(context.getBean(AuditService.class)).logEvent(eq("ACCOUNTING"),any(),eq("LOAN_RECEIPT_POSTED"),any(),any(),any(),any(),any(),any(),any(),any());processBatch(batch,true);
        assertThat(batches().get(actor,batch).posted()).isZero();assertThat(jdbc.queryForObject("select count(*) from loan_repayment_transactions where sacco_id=?",Integer.class,institution)).isZero();
        reset(context.getBean(AuditService.class));processBatch(batch,false);processBatch(batch,true);assertThat(batches().get(actor,batch).posted()).isEqualTo(1);
    }
    @Test void batchScopeOwnershipAndCurrentPermissionAreRechecked(){
        seedHistoricalPolicy();var loan=disbursed("D-BATCH-SCOPE");var batch=batches().create(actor,"Scoped batch");var item=batchItem(batch,receiptForm(loan));
        assertThatThrownBy(()->batches().get(operator("B2"),batch)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->batches().save(operator("B1"),batch,item,new com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.SaveItem(0,receiptForm(loan)))).isInstanceOf(AccessDeniedException.class);
        processBatch(batch,false);batches().start(actor,batch,batches().get(actor,batch).batch().version(),true);
        when(context.getBean(UserClaimService.class).effectiveClaims(eq(actor.getMemberId()),anyCollection(),anyBoolean())).thenReturn(Set.of());new com.sacco.mvp.accounting.batch.service.ReceiptBatchWorker(context.getBean(com.sacco.mvp.accounting.batch.service.ReceiptBatchWorkerService.class),new io.micrometer.core.instrument.simple.SimpleMeterRegistry()).tick();
        assertThat(jdbc.queryForObject("select state from receipt_batch where id=?",String.class,batch)).isEqualTo("PARTIAL");assertThat(jdbc.queryForObject("select count(*) from accountant_loan_receipt where sacco_id=?",Integer.class,institution)).isZero();
    }
    @Test void expiredWorkerIsFencedAndStopPreservesPostedReceiptsForResume(){
        seedHistoricalPolicy();var loan=disbursed("D-FENCING");var batch=batches().create(actor,"Worker recovery");batchItem(batch,receiptForm(loan));batchItem(batch,receiptForm(loan));processBatch(batch,false);
        batches().start(actor,batch,batches().get(actor,batch).batch().version(),true);var worker=context.getBean(com.sacco.mvp.accounting.batch.service.ReceiptBatchWorkerService.class);var expired=worker.claim().orElseThrow();assertThat(expired.id()).isEqualTo(batch);
        jdbc.update("update receipt_batch set lease_until=? where id=?",OffsetDateTime.of(TODAY.minusDays(1),LocalTime.MIDNIGHT,ZoneOffset.UTC),batch);
        var recovered=worker.claim().orElseThrow();assertThat(recovered.workerToken()).isNotEqualTo(expired.workerToken());worker.step(expired);assertThat(batches().get(actor,batch).posted()).isZero();worker.step(recovered);assertThat(batches().get(actor,batch).posted()).isEqualTo(1);
        batches().cancel(actor,batch);worker.step(worker.claim().orElseThrow());assertThat(batches().get(actor,batch).batch().state()).isEqualTo("CANCELLED");assertThat(batches().get(actor,batch).posted()).isEqualTo(1);
        processBatch(batch,false);processBatch(batch,true);assertThat(batches().get(actor,batch).posted()).isEqualTo(2);assertThat(jdbc.queryForObject("select count(*) from accountant_loan_receipt where sacco_id=?",Integer.class,institution)).isEqualTo(2);
    }
    @Test void duplicateCollectionReferencesFailBatchValidationBeforeMoneyIsPosted(){
        seedHistoricalPolicy();var loan=disbursed("D-BATCH-DUP");var batch=batches().create(actor,"Duplicate reference");var first=receiptForm(loan);var second=receiptForm(loan);second.setReference(first.getReference());batchItem(batch,first);batchItem(batch,second);processBatch(batch,false);
        assertThat(batches().get(actor,batch).failed()).isEqualTo(2);assertThat(batches().items(actor,batch,0,"").rows()).allMatch(i->i.failureKey().equals("batch.error.duplicate"));assertThat(batches().get(actor,batch).posted()).isZero();
    }
    @Test void systemActivityAndTransactionsCannotBeModifiedOrUsedAsOrdinaryTemplates(){
        var activity=jdbc.queryForObject("select id from gl_activity where sacco_id=? and system_key='LOAN_REPAYMENT'",UUID.class,institution);var transaction=jdbc.queryForObject("select id from gl_transaction_code where sacco_id=? and system_key='REPAYMENT_PRINCIPAL'",UUID.class,institution);
        assertThatThrownBy(()->jdbc.update("update gl_activity set active=false where id=?",activity)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("update gl_transaction_code set name='Edited' where id=?",transaction)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(jdbc.queryForObject("select count(*) from gl_transaction_code where sacco_id=? and system_key is not null",Integer.class,institution)).isEqualTo(2);
    }
    @BeforeAll void start(){context=new AnnotationConfigApplicationContext(Config.class);jdbc=context.getBean(JdbcTemplate.class);service=context.getBean(LoanRecordingService.class);ledger=context.getBean(LoanRepaymentLedgerService.class);}
    @AfterAll void stop(){if(context!=null)context.close();}
    @BeforeEach void fixture(){
        reset(context.getBean(AuditService.class),context.getBean(MemberDirectoryService.class),context.getBean(UserClaimService.class),context.getBean(SaccoRegistryService.class));
        institution="LR-"+UUID.randomUUID();jdbc.update("insert into registered_saccos(sacco_id,sacco_name,active,created_at,updated_at) values(?,'Synthetic loan institution',true,now(),now())",institution);
        for(String branch:List.of("B1","B2"))jdbc.update("insert into sacco_stations(id,sacco_id,station_id,active,access_status,created_at,updated_at) values(?,?,?,true,'ACTIVE',now(),now())",UUID.randomUUID(),institution,branch);
        actor=operator("B1");client=UUID.randomUUID();jdbc.update("insert into members(id,sacco_id,station_id,member_no,full_name,status,position,is_member,password_hash,created_at) values(?,?, 'B1',?,'Synthetic client','ACTIVE','MEMBER',true,'test-only',now())",client,institution,client.toString());
        product=UUID.randomUUID();jdbc.update("insert into loan_product_settings(id,sacco_id,loan_type,active,product_status,product_code,product_name,form_schema,guarantors_required,interest_rate,interest_method,repayment_frequency,min_repayment_months,max_repayment_months,created_at,updated_at) values(?,?,'DEVELOPMENT_LOAN',true,'ACTIVE','BUSINESS','Synthetic business loan','{}',0,0.2400,'FLAT_RATE','MONTHLY',1,24,now(),now())",product,institution);
        jdbc.update("update loan_product_settings set manager_review_required=false,loan_officer_review_required=false,chairperson_review_required=false,board_review_required=false,committee_review_required=false,accountant_review_required=false,disbursement_officer_required=true,disbursement_proof_required=false where id=?",product);
        UUID customConfiguration=UUID.randomUUID();jdbc.update("insert into loan_product_settings(id,sacco_id,loan_type,active,product_status,product_code,product_name,form_schema,guarantors_required,interest_rate,interest_method,repayment_frequency,min_repayment_months,max_repayment_months,manager_review_required,loan_officer_review_required,chairperson_review_required,board_review_required,committee_review_required,accountant_review_required,disbursement_officer_required,disbursement_proof_required,custom_recording_configuration,created_at,updated_at) values(?,?,'CUSTOMIZED_LOAN',true,'ACTIVE','CUSTOM','Customised loan','{}',0,0,'FLAT_RATE','MONTHLY',1,600,false,false,false,false,false,false,true,false,true,now(),now())",customConfiguration,institution);
        jdbc.update("insert into loan_product_board_reviewers(id,loan_product_setting_id,sacco_id,review_stage,board_member_id,created_at) values(?,?,?,'DISBURSEMENT_OFFICER',?,now())",UUID.randomUUID(),customConfiguration,institution,actor.getMemberId());

        cash=account("CASH","ASSET","DEBIT","POSTING","CASH");principal=account("LOANS","ASSET","DEBIT","POSTING","LOAN_PRINCIPAL");interest=account("INTEREST","LIABILITY","CREDIT","POSTING","CLEARING");
        jdbc.update("update loan_product_settings set principal_gl_account_id=?,interest_gl_account_id=? where sacco_id=?",principal,interest,institution);
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
    @Test void applicationsFollowFrozenReviewOrderBeforeTellerPosts(){
        jdbc.update("update loan_product_settings set manager_review_required=true,manager_priority=1,accountant_review_required=true,accountant_priority=6 where id=?",product);
        var f=form();f.setFirstPaymentDate(null);var id=service.save(actor,f);
        assertThat(status(id)).isEqualTo("READY_FOR_MANAGER");
        assertThatThrownBy(()->service.disburse(actor,post(id,"1000.00","EARLY"))).hasMessage("recording.error.state");
        assertThatThrownBy(()->jdbc.update("update loan_applications set status='READY_FOR_DISBURSEMENT' where id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        jdbc.update("update loan_product_settings set manager_review_required=false,accountant_review_required=false,interest_rate=0.9 where id=?",product);
        advance(id,ApprovalWorkflowStage.MANAGER);assertThat(status(id)).isEqualTo("AWAITING_ACCOUNTANT");
        advance(id,ApprovalWorkflowStage.ACCOUNTANT);assertThat(status(id)).isEqualTo("READY_FOR_DISBURSEMENT");
        var p=post(id,"1000.00","REVIEWED");p.setFirstPaymentDate(TODAY.plusMonths(1));var saved=service.disburse(actor,p);
        assertThat(status(id)).isEqualTo("DISBURSED");assertThat(service.disburse(actor,p).id()).isEqualTo(saved.id());
        assertThat(service.detail(actor,id).contractualInterest()).isEqualByComparingTo("20.00");
        assertThat(jdbc.queryForObject("select count(*) from manager_reviews where loan_application_id=? and review_stage='DISBURSEMENT_OFFICER' and decision='ACCEPT'",Integer.class,id)).isEqualTo(1);
        assertThat(jdbc.queryForObject("select count(*) from gl_journal where sacco_id=?",Integer.class,institution)).isEqualTo(1);
    }
    @Test void customisedApplicationsFreezeAssignedTellersAndKeepBranchIsolation(){
        var f=form();f.setLoanMode("CUSTOM");f.setCustomApplicationCharge(BigDecimal.ZERO);f.setCustomProcessingCharge(BigDecimal.ZERO);f.setInsurancePremium(BigDecimal.ZERO);f.setProductId(null);f.setInterestPercent(new BigDecimal("12"));
        var id=service.save(actor,f);var other=operator("B1");
        var config=context.getBean(LoanProductSettingRepository.class).findBySaccoIdAndCustomRecordingConfigurationTrue(institution).orElseThrow();
        jdbc.update("update loan_product_board_reviewers set board_member_id=? where loan_product_setting_id=? and review_stage='DISBURSEMENT_OFFICER'",other.getMemberId(),config.getId());
        assertThatThrownBy(()->service.disburse(other,post(id,"1000.00","WRONG-TELLER"))).hasMessage("recording.error.customTeller");
        assertThatThrownBy(()->service.postingAccounts(other,id,"money","",0)).hasMessage("recording.error.customTeller");
        assertThatThrownBy(()->service.disburse(operator("B2"),post(id,"1000.00","WRONG-BRANCH"))).isInstanceOf(AccessDeniedException.class);
        service.disburse(actor,post(id,"1000.00","D-ASSIGNED-TELLER"));assertThat(status(id)).isEqualTo("DISBURSED");
    }
    @Test void databaseRejectsFundingWithoutPostedAccountingAndSnapshotTampering(){
        var f=form();f.setFirstPaymentDate(null);var id=service.save(actor,f);
        assertThatThrownBy(()->jdbc.update("update loan_applications set status='DISBURSED',disbursement_date=?,first_repayment_date=? where id=?",TODAY,TODAY.plusMonths(1),id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(status(id)).isEqualTo("READY_FOR_DISBURSEMENT");
        assertThatThrownBy(()->jdbc.update("update loan_applications set policy_snapshot='{}' where id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThat(service.detail(actor,id).loan().outstandingPrincipal()).isZero();
    }
    @Test void historicalRecordedApplicationCanEnterReviewWithoutRepricing(){
        var original=service.save(actor,form());var legacy=UUID.randomUUID();
        jdbc.update("""
            insert into loan_applications(id,application_number,sacco_id,station_id,applicant_member_id,loan_type,loan_product_setting_id,amount,tenor_months,status,required_guarantors,form_data,policy_snapshot,financial_snapshot,repayment_frequency,first_repayment_date,installment_amount,created_at,updated_at,version)
            select ?,application_number+5000,sacco_id,station_id,applicant_member_id,loan_type,loan_product_setting_id,amount,tenor_months,'RECORDED',required_guarantors,form_data,'{}',financial_snapshot,repayment_frequency,first_repayment_date,installment_amount,now(),now(),0 from loan_applications where id=?
            """,legacy,original);
        jdbc.update("""
            insert into accountant_loan_record(loan_id,sacco_id,station_id,request_key,payload_hash,actor_id,recorded_at,application_date,requested_principal,first_payment_date,product_name,metadata)
            select ?,sacco_id,station_id,?,payload_hash,actor_id,now(),application_date,requested_principal,first_payment_date,product_name,metadata from accountant_loan_record where loan_id=?
            """,legacy,UUID.randomUUID(),original);
        jdbc.update("update loan_product_settings set accountant_review_required=true,accountant_priority=6,interest_rate=0.9 where id=?",product);
        service.submitRecordedApplication(actor,legacy);service.submitRecordedApplication(actor,legacy);
        assertThat(status(legacy)).isEqualTo("AWAITING_ACCOUNTANT");assertThat(service.detail(actor,legacy).contractualInterest()).isEqualByComparingTo("20.00");
        assertThatThrownBy(()->service.submitRecordedApplication(operator("B2"),legacy)).isInstanceOf(AccessDeniedException.class);
    }
    private String status(UUID id){return jdbc.queryForObject("select status from loan_applications where id=?",String.class,id);}
    private void advance(UUID id,ApprovalWorkflowStage stage){
        new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class)).executeWithoutResult(tx->{
            var repository=context.getBean(LoanApplicationRepository.class);var application=repository.findForDisbursement(id).orElseThrow();
            context.getBean(WorkflowRoutingService.class).advanceAfterApproval(application,stage,actor.getMemberId());repository.saveAndFlush(application);
        });
    }
    @Test void productFiltersUseCreatedProductIdentityAcrossLoansPostingsReportsAndTotals(){
        var other=UUID.randomUUID();
        jdbc.update("""
            insert into loan_product_settings(id,sacco_id,loan_type,active,product_status,product_code,product_name,form_schema,
            guarantors_required,interest_rate,interest_method,repayment_frequency,min_repayment_months,max_repayment_months,created_at,updated_at)
            select ?,sacco_id,loan_type,active,product_status,'OTHER','Other business loan',form_schema,
            guarantors_required,interest_rate,interest_method,repayment_frequency,min_repayment_months,max_repayment_months,now(),now()
            from loan_product_settings where id=?
            """,other,product);
        jdbc.update("update loan_product_settings set manager_review_required=false,loan_officer_review_required=false,chairperson_review_required=false,board_review_required=false,committee_review_required=false,accountant_review_required=false,disbursement_officer_required=true,disbursement_proof_required=false where id=?",other);
        jdbc.update("update loan_product_settings set principal_gl_account_id=?,interest_gl_account_id=? where sacco_id=?",principal,interest,institution);
        var first=service.save(actor,form());var secondForm=form();secondForm.setProductId(other);var second=service.save(actor,secondForm);
        service.disburse(actor,post(first,"1000.00","D-PRODUCT-1"));service.disburse(actor,post(second,"1000.00","D-PRODUCT-2"));
        var payment=post(first,"120.00","PRODUCT-R1");payment.setInterestAccountId(interest);service.repay(actor,payment);
        var secondPayment=post(second,"120.00","PRODUCT-R2");secondPayment.setInterestAccountId(interest);service.repay(actor,secondPayment);
        var filter=new Filter("",null,null,"","newest",0,null,product);
        assertThat(service.list(actor,filter).rows()).extracting(LoanRow::id).containsExactly(first);
        for(var kind:List.of("DISBURSEMENT","PAYMENT")){
            assertThat(service.register(actor,kind,filter).rows()).extracting(Posting::loanId).containsExactly(first);
            assertThat(service.report(actor,kind,filter)).extracting(Posting::loanId).containsExactly(first);
            assertThat(service.totals(actor,kind,filter).count()).isEqualTo(1);
        }
        assertThat(service.totals(actor,"PAYMENT",filter).amount()).isEqualByComparingTo("120.00");
        jdbc.update("update loan_product_settings set active=false,product_name='Renamed business loan' where id=?",product);
        assertThat(service.list(actor,filter).rows()).extracting(LoanRow::id).containsExactly(first);
        assertThat(service.choices(actor,"filter-products","",0)).extracting(Choice::id).contains(product,other);
        assertThat(service.filterProduct(actor,product).label()).isEqualTo("Renamed business loan");
        assertThat(service.register(actor,"PAYMENT",filter).rows()).extracting(Posting::product).containsExactly("Synthetic business loan");
        assertThat(service.movements(actor,client,0).rows()).extracting(Posting::product).contains("Synthetic business loan","Other business loan");
        assertThat(service.list(operator("B2"),filter).rows()).isEmpty();
        assertThat(service.report(operator("B2"),"PAYMENT",filter)).isEmpty();
        assertThat(service.list(actor,new Filter("",null,null,"","newest",1,null,product)).rows()).isEmpty();
        assertThatThrownBy(()->service.filterProduct(actor,UUID.randomUUID())).hasMessage("recording.error.filter");
    }
    @Test void directPaymentRetryIndependentReversalAndClientBalanceAgree(){
        var id=service.save(actor,form());service.disburse(actor,post(id,"1000.00","D1"));var f=post(id,"120.00","R1");f.setPrincipalAccountId(null);f.setInterestAccountId(interest);
        var paid=service.repay(actor,f);assertThat(paid.principal()).isEqualByComparingTo("100.00");assertThat(paid.interest()).isEqualByComparingTo("20.00");assertThat(paid.principalBalance()).isEqualByComparingTo("900.00");
        assertThat(service.repay(actor,f).id()).isEqualTo(paid.id());assertThat(service.statement(actor,client,0).principal()).isEqualByComparingTo("900.00");
        var postedFilter=new Filter("R1",TODAY,TODAY,"POSTED","newest",0,null);
        var postedTotals=service.totals(actor,"PAYMENT",postedFilter);
        assertThat(postedTotals.count()).isEqualTo(1);assertThat(postedTotals.amount()).isEqualByComparingTo("120.00");
        assertThat(postedTotals.principal()).isEqualByComparingTo("100.00");assertThat(postedTotals.interest()).isEqualByComparingTo("20.00");
        assertThat(jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where sacco_id=? and account_id=?",BigDecimal.class,institution,cash)).isEqualByComparingTo("-880.00");
        assertThat(jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where sacco_id=? and account_id=?",BigDecimal.class,institution,principal)).isEqualByComparingTo("900.00");
        assertThat(jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where sacco_id=? and account_id=?",BigDecimal.class,institution,interest)).isEqualByComparingTo("-20.00");
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
        assertThat(jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where sacco_id=? and account_id=?",BigDecimal.class,institution,cash)).isEqualByComparingTo("-1000.00");
        assertThat(jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where sacco_id=? and account_id=?",BigDecimal.class,institution,interest)).isZero();
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
        assertThatThrownBy(()->service.save(actor,form())).hasMessage("charges.error.policy");
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
    @Test void individualLoanStatementKeepsOpeningOnLaterPagesAndChecksOwnership(){
        var id=service.save(actor,form());service.disburse(actor,post(id,"1000.00","D1"));
        var page=service.loanStatement(actor,client,id,1,false);
        assertThat(page.source().loans()).extracting(LoanRow::id).containsExactly(id);
        assertThat(page.source().terms()).extracting(StatementTerms::loanId).containsExactly(id);
        assertThat(page.source().movements()).isEmpty();assertThat(page.hasNext()).isFalse();
        assertThat(page.opening().principalBalance()).isEqualByComparingTo("1000.00");
        assertThat(service.loanStatement(actor,client,id,1,true).source().movements()).hasSize(1);
        assertThatThrownBy(()->service.loanStatement(operator("B2"),client,id,0,false)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.loanStatement(actor,UUID.randomUUID(),id,0,false)).isInstanceOf(AccessDeniedException.class);
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
    @Test void newRecordingDefersFirstDateAndAssignsUniqueNumbersOnAtomicDisbursement(){
        var f=form();f.setFirstPaymentDate(null);f.setRequestedPrincipal(null);f.setTenureUnit("YEARS");f.setTenureValue(1);f.setMonths(null);
        var id=service.save(actor,f);assertThat(service.detail(actor,id).firstPaymentDate()).isNull();assertThat(service.detail(actor,id).tenureUnit()).isEqualTo("YEARS");
        var post=post(id,"1000.00","D-NEW-1");post.setFirstPaymentDate(LocalDate.of(2026,10,1));var saved=service.disburse(actor,post);
        long firstNumber=Long.parseLong(saved.loanNumber());assertThat(firstNumber).isPositive();assertThat(service.disburse(actor,post).id()).isEqualTo(saved.id());
        var second=disbursed("D-NEW-2");assertThat(Long.parseLong(service.detail(actor,second).loan().number())).isGreaterThan(firstNumber);
        var pending=service.save(actor,form());
        assertThatThrownBy(()->jdbc.update("update loan_applications set loan_id=? where id=?",saved.loanNumber(),pending)).isInstanceOf(org.springframework.dao.DataIntegrityViolationException.class);
        assertThatThrownBy(()->jdbc.update("update loan_applications set loan_id='99' where id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
        assertThatThrownBy(()->jdbc.update("update loan_applications set first_repayment_date=first_repayment_date+1 where id=?",id)).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void customInterestModesAndExactDayMaturitySurvivePosting(){
        var f=form();f.setLoanMode("CUSTOM");f.setCustomApplicationCharge(BigDecimal.ZERO);f.setCustomProcessingCharge(BigDecimal.ZERO);f.setInsurancePremium(BigDecimal.ZERO);f.setProductId(null);f.setFirstPaymentDate(null);f.setTenureUnit("DAYS");f.setTenureValue(10);f.setInterestPercent(new BigDecimal("12"));f.setInterestBasis("WHOLE_LOAN");
        var id=service.save(actor,f);assertThat(service.detail(actor,id).contractualInterest()).isEqualByComparingTo("120.00");
        var p=post(id,"1000.00","D-CUSTOM");p.setFirstPaymentDate(p.getEffectiveDate().plusDays(10));service.disburse(actor,p);
        assertThat(service.detail(actor,id).schedule().getLast().date()).isEqualTo(p.getEffectiveDate().plusDays(10));
        var annual=form();annual.setLoanMode("CUSTOM");annual.setCustomApplicationCharge(BigDecimal.ZERO);annual.setCustomProcessingCharge(BigDecimal.ZERO);annual.setInsurancePremium(BigDecimal.ZERO);annual.setInterestBasis("ANNUAL");annual.setInterestPercent(new BigDecimal("12"));annual.setMonths(6);annual.setFirstPaymentDate(null);var annualId=service.save(actor,annual);assertThat(service.detail(actor,annualId).contractualInterest()).isEqualByComparingTo("60.00");
        var amount=form();amount.setLoanMode("CUSTOM");amount.setCustomApplicationCharge(BigDecimal.ZERO);amount.setCustomProcessingCharge(BigDecimal.ZERO);amount.setInsurancePremium(BigDecimal.ZERO);amount.setInterestEntry("AMOUNT");amount.setInterestAmount(new BigDecimal("17.03"));var amountId=service.save(actor,amount);assertThat(service.detail(actor,amountId).contractualInterest()).isEqualByComparingTo("17.03");
    }
    @Test void uploadedEvidenceIsScopedFingerprintProtectedAndRollsBackWithTheLoan() throws Exception {
        var f=form();var file=new org.springframework.mock.web.MockMultipartFile("attachments","evidence.pdf","application/pdf","%PDF-1.4 synthetic evidence".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        var id=service.save(actor,f,List.of(file));var files=service.attachments(actor,id);assertThat(files).hasSize(1);String attachment=files.getFirst().get("id").toString();assertThat(service.attachment(actor,id,attachment).getContent()).containsExactly(file.getBytes());
        assertThat(service.save(actor,f,List.of(file))).isEqualTo(id);
        var changed=new org.springframework.mock.web.MockMultipartFile("attachments","evidence.pdf","application/pdf","%PDF-1.4 changed".getBytes(java.nio.charset.StandardCharsets.UTF_8));assertThatThrownBy(()->service.save(actor,f,List.of(changed))).hasMessage("recording.error.retry");
        assertThatThrownBy(()->service.attachment(operator("B2"),id,attachment)).isInstanceOf(AccessDeniedException.class);
        var invalid=new org.springframework.mock.web.MockMultipartFile("attachments","evidence.pdf","application/pdf","not a PDF".getBytes());assertThatThrownBy(()->service.save(actor,form(),List.of(invalid))).isInstanceOf(IllegalArgumentException.class);
    }
    @Test void globalNumbersDoNotRestartAcrossPeriodsAndConcurrentAssignmentsNeverReuseValues() throws Exception {
        var repo=new com.sacco.mvp.accounting.loans.repository.LoanNumberRepository(jdbc);var transactions=new org.springframework.transaction.support.TransactionTemplate(context.getBean(PlatformTransactionManager.class));
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();for(var period:List.of(first,second))jdbc.update("insert into accounting_period(id,sacco_id,starts_on,ends_on,state,created_by,created_at) values(?,?,?,?,'OPEN',?,now())",period,institution,period.equals(first)?LocalDate.of(2030,1,1):LocalDate.of(2031,1,1),period.equals(first)?LocalDate.of(2030,12,31):LocalDate.of(2031,12,31),actor.getMemberId());
        var numbering=new com.sacco.mvp.accounting.loans.service.LoanDisbursementNumberService(repo,new VoucherRepository(jdbc),context.getBean(ApplicationClock.class));
        var firstLoan=LoanApplication.builder().saccoId(institution).build();var secondLoan=LoanApplication.builder().saccoId(institution).build();
        transactions.executeWithoutResult(t->numbering.assign(firstLoan,actor.getMemberId(),LocalDate.of(2030,6,1)));
        transactions.executeWithoutResult(t->numbering.assign(secondLoan,actor.getMemberId(),LocalDate.of(2031,6,1)));
        assertThat(firstLoan.getLoanAccountingPeriodId()).isEqualTo(first);assertThat(secondLoan.getLoanAccountingPeriodId()).isEqualTo(second);
        assertThat(Long.parseLong(secondLoan.getLoanId())).isGreaterThan(Long.parseLong(firstLoan.getLoanId()));
        try(var pool=Executors.newFixedThreadPool(4)){var work=new ArrayList<Callable<Long>>();for(int i=0;i<8;i++)work.add(()->transactions.execute(t->repo.next()));var results=pool.invokeAll(work);var values=new ArrayList<Long>();for(var result:results)values.add(result.get());assertThat(values).hasSize(8).doesNotHaveDuplicates().allMatch(value->value>Long.parseLong(secondLoan.getLoanId()));}
        Long rolledBack=transactions.execute(t->{long value=repo.next();t.setRollbackOnly();return value;});assertThat(transactions.<Long>execute(t->repo.next())).isGreaterThan(rolledBack);
    }
    @Test void concurrentIdenticalSignaturesRegisterOnceAndLaterEditsAreAudited() throws Exception {
        var password="synthetic-signature-password";
        jdbc.update("update members set password_hash=? where id=?",new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder().encode(password),actor.getMemberId());
        var signatures=context.getBean(StaffSignatureService.class);
        try(var pool=Executors.newFixedThreadPool(2)) {
            var work=List.<Callable<Void>>of(()->{signatures.register(actor,"Test Accountant",password,true);return null;},()->{signatures.register(actor,"Test Accountant",password,true);return null;});
            for(var result:pool.invokeAll(work))result.get();
        }
        var saved=context.getBean(MemberRepository.class).findById(actor.getMemberId()).orElseThrow();
        assertThat(saved.getSignatureText()).isEqualTo("Test A");assertThat(saved.getSignatureRegisteredAt()).isNotNull();
        signatures.register(actor,"Different Name",password,true);
        assertThat(context.getBean(MemberRepository.class).findById(actor.getMemberId()).orElseThrow().getSignatureText()).isEqualTo("Different N");
        verify(context.getBean(AuditService.class)).log(eq("STAFF_USER"),eq(actor.getMemberId()),eq("STAFF_UPDATE_OWN_SIGNATURE"),eq(actor.getMemberId()),any(),any());
        verify(context.getBean(AuditService.class),times(1)).log(eq("STAFF_USER"),eq(actor.getMemberId()),eq("STAFF_REGISTER_OWN_SIGNATURE"),eq(actor.getMemberId()),any(),any());
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
        @Bean com.sacco.mvp.accounting.adjustments.service.LoanAdjustmentService adjustments(JdbcTemplate jdbc,GeneralLedgerService accounting,ApplicationClock clock,AuditService audit){return new com.sacco.mvp.accounting.adjustments.service.LoanAdjustmentService(new com.sacco.mvp.accounting.adjustments.repository.LoanAdjustmentRepository(jdbc),accounting,new VoucherRepository(jdbc),clock,audit,chargeService(jdbc,accounting,clock,audit,JsonMapper.builder().findAndAddModules().build()));}
        @Bean StaffSignatureService signatures(MemberRepository members,AuditService audit){return new StaffSignatureService(members,new NameSignatureService(),new org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder(),audit);}
        @Bean MemberDirectoryService directory(){return mock(MemberDirectoryService.class);}
        @Bean UserClaimService claims(){return mock(UserClaimService.class);}
        @Bean SaccoRegistryService institutions(){return mock(SaccoRegistryService.class);}
        @Bean GeneralLedgerService accounting(JdbcTemplate j,AuditService audit,ApplicationClock clock,UserClaimService claims,MemberDirectoryService directory,SaccoRegistryService institutions){return new GeneralLedgerService(new GeneralLedgerRepository(j),new AccessControlService(),audit,clock,claims,directory,institutions);}
        @Bean com.sacco.mvp.accounting.charges.service.LoanChargeService chargeService(JdbcTemplate j,GeneralLedgerService accounting,ApplicationClock clock,AuditService audit,ObjectMapper mapper){return new com.sacco.mvp.accounting.charges.service.LoanChargeService(new com.sacco.mvp.accounting.charges.repository.LoanChargeRepository(j),accounting,new VoucherRepository(j),clock,audit,mapper);}
        @Bean LoanPostingGuard guard(JdbcTemplate j,ObjectMapper mapper){return new LoanPostingGuard(new LoanPostingAuthorizationRepository(j));}
        @Bean com.sacco.mvp.accounting.repayment.service.RepaymentPolicyService repaymentPolicy(JdbcTemplate j,GeneralLedgerService accounting,ApplicationClock clock,AuditService audit){return new com.sacco.mvp.accounting.repayment.service.RepaymentPolicyService(new com.sacco.mvp.accounting.repayment.repository.RepaymentPolicyRepository(j));}
        @Bean com.sacco.mvp.accounting.batch.repository.ReceiptBatchRepository batchRepository(JdbcTemplate j,ObjectMapper mapper){return new com.sacco.mvp.accounting.batch.repository.ReceiptBatchRepository(j,mapper);}
        @Bean com.sacco.mvp.accounting.batch.service.ReceiptBatchService batches(com.sacco.mvp.accounting.batch.repository.ReceiptBatchRepository repository,com.sacco.mvp.accounting.service.LoanReceiptService receipts,LoanRecordingService loans,com.sacco.mvp.accounting.service.VoucherService vouchers,com.sacco.mvp.accounting.repayment.service.RepaymentPolicyService policies,AuditService audit,ApplicationClock clock){return new com.sacco.mvp.accounting.batch.service.ReceiptBatchService(repository,receipts,loans,vouchers,policies,audit,clock);}
        @Bean com.sacco.mvp.accounting.batch.service.ReceiptBatchWorkerService batchWorker(com.sacco.mvp.accounting.batch.repository.ReceiptBatchRepository repository,com.sacco.mvp.accounting.batch.service.ReceiptBatchService batches,com.sacco.mvp.accounting.service.LoanReceiptService receipts,LoanRecordingService loans,MemberDirectoryService members,UserClaimService claims,ApplicationClock clock){return new com.sacco.mvp.accounting.batch.service.ReceiptBatchWorkerService(repository,batches,receipts,loans,members,claims,clock);}
        @Bean com.sacco.mvp.accounting.repayment.service.LoanPrepaymentService prepayments(JdbcTemplate j,ApplicationClock clock,AuditService audit,com.sacco.mvp.accounting.service.VoucherService vouchers){return new com.sacco.mvp.accounting.repayment.service.LoanPrepaymentService(new com.sacco.mvp.accounting.repayment.repository.LoanPrepaymentRepository(j),clock,audit,vouchers);}
        @Bean LoanRepaymentLedgerService repayments(LoanLedgerRepository ledgers,LoanLedgerInstallmentRepository installments,LoanRepaymentTransactionRepository transactions,LoanRepaymentAllocationRepository allocations,LoanJournalEntryRepository journal,LoanApplicationRepository loans,ObjectMapper mapper,ApplicationClock clock,AuditService audit,LoanPostingGuard guard,com.sacco.mvp.accounting.repayment.service.LoanPrepaymentService prepayments){return new LoanRepaymentLedgerService(ledgers,installments,transactions,allocations,journal,loans,mapper,clock,new AccessControlService(),audit,guard,prepayments);}
        LoanProductWorkflowService contextWorkflows(ObjectMapper mapper,LoanProductSettingRepository products){return new LoanProductWorkflowService(mapper,products,mock(com.sacco.mvp.repository.SaccoSettingsRepository.class));}
        @Bean WorkflowRoutingService routing(ObjectMapper mapper,LoanProductSettingRepository products,com.sacco.mvp.repository.SaccoSettingsRepository settings,com.sacco.mvp.repository.BoardReviewRepository board,com.sacco.mvp.repository.LoanProductBoardReviewerRepository assigned){return new WorkflowRoutingService(settings,new ApprovalFlowService(),board,assigned,products,mock(RoleDirectoryService.class),contextWorkflows(mapper,products),mock(OutboxService.class));}
        @Bean com.sacco.mvp.grouplending.service.GroupSharedLoanService sharedLoans(JdbcTemplate j,ObjectMapper mapper,GeneralLedgerService accounting,MemberDirectoryService members,ApplicationClock clock,AuditService audit){return new com.sacco.mvp.grouplending.service.GroupSharedLoanService(new com.sacco.mvp.grouplending.repository.GroupSharedLoanRepository(j,mapper),accounting,members,clock,audit);}
        @Bean com.sacco.mvp.grouplending.service.SharedCreditExposureService sharedCredit(JdbcTemplate jdbc){var messages=new org.springframework.context.support.ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");return new com.sacco.mvp.grouplending.service.SharedCreditExposureService(new com.sacco.mvp.grouplending.repository.SharedCreditExposureRepository(jdbc),messages);}
        @Bean LoanRecordingService recording(JdbcTemplate j,LoanApplicationRepository loans,LoanProductSettingRepository products,LoanRepaymentLedgerService repayments,GeneralLedgerService accounting,ObjectMapper mapper,ApplicationClock clock,AuditService audit,com.sacco.mvp.repository.StoredUploadRepository uploadRepository,WorkflowRoutingService routing,com.sacco.mvp.repository.LoanProductBoardReviewerRepository contextReviewers,com.sacco.mvp.repository.ManagerReviewRepository contextReviews,MemberDirectoryService contextDirectory,com.sacco.mvp.grouplending.service.GroupSharedLoanService sharedLoans,com.sacco.mvp.grouplending.service.SharedCreditExposureService sharedCredit){var storage=new StoredUploadStorageService(uploadRepository);org.springframework.test.util.ReflectionTestUtils.setField(storage,"uploadsRoot","target/request-upload-fixture");var numbers=mock(ApplicationNumberService.class);var next=new AtomicLong(1000000000);when(numbers.nextFor(anyString())).thenAnswer(i->next.incrementAndGet());return new LoanRecordingService(new LoanRecordingRepository(j),loans,products,numbers,new RepaymentScheduleService(mapper,products),repayments,accounting,new VoucherRepository(j),mapper,clock,audit,new LoanAttachmentService(mapper,mock(AdminAlertService.class),storage),new com.sacco.mvp.accounting.loans.service.LoanDisbursementNumberService(new com.sacco.mvp.accounting.loans.repository.LoanNumberRepository(j),new VoucherRepository(j),clock),contextWorkflows(mapper,products),routing,contextReviewers,contextReviews,repaymentPolicy(j,accounting,clock,audit),chargeService(j,accounting,clock,audit,mapper),new com.sacco.mvp.accounting.loans.service.LoanProductAccountService(new com.sacco.mvp.accounting.loans.repository.LoanProductAccountRepository(j),clock,audit,accounting),new com.sacco.mvp.grouplending.service.GroupOrganizationFundingService(new com.sacco.mvp.grouplending.repository.GroupOrganizationFundingRepository(j),accounting,contextDirectory,clock,audit),sharedLoans,sharedCredit);}
        @Bean com.sacco.mvp.accounting.service.VoucherService vouchers(JdbcTemplate j,GeneralLedgerService accounting,ApplicationClock clock,AuditService audit,ObjectMapper json){return new com.sacco.mvp.accounting.service.VoucherService(new VoucherRepository(j),accounting,clock,audit,(JsonMapper)json,new com.sacco.mvp.accounting.approval.service.VoucherApprovalGate(new com.sacco.mvp.accounting.approval.repository.VoucherWorkflowRepository(j,json),accounting));}
        @Bean com.sacco.mvp.grouplending.repository.GroupCollectionRepository groupCollections(JdbcTemplate jdbc){return new com.sacco.mvp.grouplending.repository.GroupCollectionRepository(jdbc);}
        @Bean com.sacco.mvp.grouplending.service.GroupCollectionService groupCollectionService(com.sacco.mvp.grouplending.repository.GroupCollectionRepository r,GeneralLedgerService access,LoanRecordingService loans,com.sacco.mvp.grouplending.service.GroupSharedLoanService shared,com.sacco.mvp.accounting.service.VoucherService vouchers,ApplicationClock clock,AuditService audit,ObjectMapper json){return new com.sacco.mvp.grouplending.service.GroupCollectionService(r,access,loans,shared,vouchers,clock,audit,json);}
        @Bean com.sacco.mvp.accounting.service.LoanReceiptService receipts(JdbcTemplate j,com.sacco.mvp.accounting.service.VoucherService ordinary,LoanRecordingService loans,ApplicationClock clock,AuditService audit,ObjectMapper json,com.sacco.mvp.grouplending.service.GroupCollectionService groups){return new com.sacco.mvp.accounting.service.LoanReceiptService(new LoanReceiptRepository(j),new VoucherRepository(j),ordinary,loans,clock,audit,json,groups);}
    }
}
