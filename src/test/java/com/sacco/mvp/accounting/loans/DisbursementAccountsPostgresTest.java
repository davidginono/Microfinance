package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Term;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_recording_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class DisbursementAccountsPostgresTest {
    final LoanRecordingPostgresTest f=new LoanRecordingPostgresTest();
    UUID family,payment;
    @BeforeAll void start(){f.start();}
    @AfterAll void stop(){f.stop();}
    @BeforeEach void fixture(){
        f.fixture();var root=account("100000","Assets","HEADING","OTHER",null,true);
        var section=account("110000","Current assets","HEADING","OTHER",root,true);
        f.jdbc.update("insert into gl_account_reporting_section(account_id,sacco_id,section) values(?,?,'CURRENT_ASSETS')",section,f.institution);
        family=account("111000","Cash and Cash equivalents","HEADING","OTHER",section,true);
        payment=account("111001","Synthetic bank account","POSTING","OTHER",family,true);
    }
    UUID account(String code,String name,String kind,String purpose,UUID parent,boolean active){
        var id=UUID.randomUUID();f.jdbc.update("insert into gl_account(id,sacco_id,code,name,type,normal_balance,kind,purpose,parent_id,active,maker_id,created_at) values(?,?,?,?,'ASSET','DEBIT',?,?,?,?,?,now())",id,f.institution,code,name,kind,purpose,parent,active,f.actor.getMemberId());return id;
    }
    @Test void chooserAndPostingUseTheSameScopedCashFamilyWithoutUsingItsName(){
        var loan=f.service.save(f.actor,f.form());f.post(loan,"1000.00","D-SCOPE");
        var bank=account("BANK","Explicit bank","POSTING","BANK",null,true);
        account("111002","Inactive cash","POSTING","OTHER",family,false);
        var unrelated=account("UNRELATED","Cash and Cash equivalents","HEADING","OTHER",null,true);
        var wrong=account("UNRELATED-1","Unrelated posting account","POSTING","OTHER",unrelated,true);
        assertThat(f.service.postingAccounts(f.actor,loan,"money","",0)).extracting(c->c.id()).containsExactlyInAnyOrder(payment,bank,f.cash);
        assertThat(f.service.postingAccounts(f.actor,loan,"money","Synthetic bank",0)).extracting(c->c.id()).containsExactly(payment);
        assertThat(f.service.postingAccount(f.actor,loan,payment,"MONEY").id()).isEqualTo(payment);
        assertThatThrownBy(()->f.service.postingAccount(f.actor,loan,family,"MONEY")).hasMessage("recording.error.account");
        assertThatThrownBy(()->f.service.postingAccount(f.actor,loan,wrong,"MONEY")).hasMessage("recording.error.account");
        assertThatThrownBy(()->f.service.receiptMoneyAccount(f.actor,payment)).hasMessage("recording.error.account");
        assertThatThrownBy(()->f.service.postingAccounts(f.operator("B2"),loan,"money","",0)).isInstanceOf(AccessDeniedException.class);
        for(int n=3;n<=32;n++)account(String.format("111%03d",n),"Synthetic cash "+n,"POSTING","OTHER",family,true);
        assertThat(f.service.postingAccounts(f.actor,loan,"money","",0)).hasSize(26);
        assertThat(f.service.postingAccounts(f.actor,loan,"money","",1)).hasSize(8);
    }
    @Test void disbursementPostsOnceToTheSelectedAccountPrincipalAndLoanLedger(){
        var loan=f.service.save(f.actor,f.form());var form=f.post(loan,"1000.00","D-FAMILY");form.setMoneyAccountId(payment);
        var posted=f.service.disburse(f.actor,form);assertThat(f.service.disburse(f.actor,form).id()).isEqualTo(posted.id());
        assertThat(f.jdbc.queryForObject("select state from gl_journal where id=?",String.class,posted.journalId())).isEqualTo("POSTED");
        assertThat(f.jdbc.queryForObject("select sum(credit-debit) from gl_journal_line where journal_id=? and account_id=?",BigDecimal.class,posted.journalId(),payment)).isEqualByComparingTo("1000.00");
        assertThat(f.jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where journal_id=? and account_id=?",BigDecimal.class,posted.journalId(),f.principal)).isEqualByComparingTo("1000.00");
        assertThat(f.jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where journal_id=?",BigDecimal.class,posted.journalId())).isZero();
        assertThat(f.jdbc.queryForObject("select principal from loan_ledgers where loan_application_id=?",BigDecimal.class,loan)).isEqualByComparingTo("1000.00");
        assertThat(f.jdbc.queryForObject("select count(*) from accountant_loan_post where loan_id=?",Integer.class,loan)).isEqualTo(1);
        assertThat(f.jdbc.queryForObject("select purpose from gl_account where id=?",String.class,payment)).isEqualTo("OTHER");
        var correction=f.adjustments().request(f.actor,f.adjustment(loan,"DISBURSEMENT_REVERSAL"));
        f.adjustments().decide(f.operator("B1"),correction,UUID.randomUUID(),true,"Synthetic independently verified cash return");
        assertThat(f.jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where account_id=?",BigDecimal.class,payment)).isZero();
    }
    @Test void deductedChargesAndIndependentFundingCorrectionsKeepTheSelectedLedger(){
        f.chargePolicy("SEPARATE");var loan=f.chargedLoan();var form=f.chargedFunding(loan);form.setMoneyAccountId(payment);form.setEvidence("");
        form.setDeductChargeIds(f.service.chargeSummary(f.actor,loan).plan().terms().stream().filter(t->t.kind().equals("INSURANCE")).map(Term::id).toList());
        var posted=f.service.disburse(f.actor,form);
        assertThat(f.jdbc.queryForObject("select sum(credit-debit) from gl_journal_line where journal_id=? and account_id=?",BigDecimal.class,posted.journalId(),payment)).isEqualByComparingTo("990.00");
        var correction=f.adjustments().request(f.actor,f.adjustment(loan,"DISBURSEMENT_REVERSAL"));
        f.adjustments().decide(f.operator("B1"),correction,UUID.randomUUID(),true,"Synthetic independently verified cash return");
        assertThat(f.jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where account_id=?",BigDecimal.class,payment)).isZero();
        assertThat(f.service.chargeSummary(f.actor,loan).balance().assessed()).isZero();
    }
    @Test void topUpPostsNetCashToTheSelectedCashFamilyAccount(){
        var source=f.topUpSource();var loan=f.recordTopUp(source);var form=f.topUpPost(loan);form.setMoneyAccountId(payment);
        var posted=f.service.disburse(f.actor,form);var topUp=f.service.topUp(f.actor,loan).orElseThrow();
        assertThat(f.jdbc.queryForObject("select sum(credit-debit) from gl_journal_line where journal_id=? and account_id=?",BigDecimal.class,posted.journalId(),payment)).isEqualByComparingTo(topUp.cash());
        assertThat(f.jdbc.queryForObject("select principal from loan_ledgers where loan_application_id=?",BigDecimal.class,loan)).isEqualByComparingTo("1500.00");
        f.service.reverseTopUp(f.operator("B1"),loan,UUID.randomUUID(),LoanRecordingPostgresTest.TODAY,"Synthetic mistaken transfer","Synthetic independently verified cash return");
        assertThat(f.jdbc.queryForObject("select sum(debit-credit) from gl_journal_line where account_id=?",BigDecimal.class,payment)).isZero();
    }
}
