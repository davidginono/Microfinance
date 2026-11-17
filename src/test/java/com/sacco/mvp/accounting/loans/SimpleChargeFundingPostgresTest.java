package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_flexible_repayment_[a-f0-9]{8}_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class SimpleChargeFundingPostgresTest {
    final LoanRecordingPostgresTest f=new LoanRecordingPostgresTest();
    @BeforeAll void start(){f.start();}
    @AfterAll void stop(){f.stop();}
    @BeforeEach void setup(){f.fixture();f.chargePolicy("SEPARATE");}
    AccountMappingForm mappings(){var p=f.chargeService().policy(f.actor,f.product).orElseThrow();var form=new AccountMappingForm();
        form.setProductId(f.product);form.setExpectedVersion(p.id());form.setReceivableAccountId(f.chargeAccount);
        form.setApplicationAccountId(f.managementAccount);form.setProcessingAccountId(f.processingAccount);form.setInsuranceAccountId(f.insuranceAccount);return form;}
    UUID disabledToday(){var p=new PolicyForm();p.setProductId(f.product);p.setExpectedVersion(f.chargeService().policy(f.actor,f.product).orElseThrow().id());
        p.setEffectiveFrom(LoanRecordingPostgresTest.TODAY);p.setEnabled(false);p.setApplicationAccountId(f.managementAccount);p.setProcessingAccountId(f.processingAccount);
        return f.chargeService().savePolicy(f.actor,p);}
    @Test void completeSameDayMappingsFundPendingChargesWithoutPolicyOrReferenceFields(){
        UUID disabled=disabledToday();var entry=f.form();entry.setApplicationDate(LoanRecordingPostgresTest.TODAY);entry.setFirstPaymentDate(LoanRecordingPostgresTest.TODAY.plusMonths(1));
        entry.setInsurancePremium(new java.math.BigDecimal("10"));entry.setEvidence("Synthetic signed funding agreement");entry.setInsuranceEvidence("Synthetic premium / "+UUID.randomUUID());
        UUID loan=f.service.save(f.actor,entry);
        assertThat(f.jdbc.queryForObject("select financial_snapshot->'recordedCharges'->>'supported' from loan_applications where id=?",String.class,loan)).isEqualTo("false");
        UUID active=f.chargeService().saveAccountMappings(f.actor,mappings());
        var policy=f.chargeService().policy(f.actor,f.product).orElseThrow();assertThat(policy.id()).isEqualTo(active);assertThat(policy.enabled()).isTrue();
        assertThat(policy.policyReference()).isEmpty();assertThat(policy.agreementReference()).isEmpty();assertThat(policy.insurerVerification()).isEmpty();
        assertThat(policy.insurer()).isEqualTo(f.jdbc.queryForObject("select name from gl_account where id=?",String.class,f.insuranceAccount));
        var funding=f.chargedFunding(loan);funding.setEffectiveDate(LoanRecordingPostgresTest.TODAY);f.service.disburse(f.actor,funding);
        assertThat(f.chargeService().balance(f.actor,loan,LoanRecordingPostgresTest.TODAY).outstanding()).isEqualByComparingTo("30");
        assertThat(f.jdbc.queryForObject("select policy_id from loan_charge_contract where loan_id=?",UUID.class,loan)).isEqualTo(active);
        assertThat(f.jdbc.queryForObject("select enabled from loan_charge_policy where id=?",Boolean.class,disabled)).isFalse();
    }
    @Test void replayIsIdempotentAndSameDaySelectionRejectsStaleWriters(){
        disabledToday();var form=mappings();var stale=mappings();UUID active=f.chargeService().saveAccountMappings(f.actor,form);
        assertThat(f.chargeService().saveAccountMappings(f.actor,form)).isEqualTo(active);
        assertThatThrownBy(()->f.chargeService().saveAccountMappings(f.actor,stale)).hasMessage("charges.error.stale");
        assertThat(f.chargeService().policy(f.actor,f.product).orElseThrow().id()).isEqualTo(active);
        assertThat(f.jdbc.queryForObject("select count(*) from loan_charge_policy where request_key=?",Integer.class,form.getRequestKey())).isEqualTo(1);
    }
    @Test void historicalReferencesAndFrozenTermsStayUnchanged(){
        UUID loan=f.chargedLoan();var before=f.chargeService().quotedTerms(f.actor,loan);var old=f.chargeService().policy(f.actor,f.product).orElseThrow();
        f.chargeService().saveAccountMappings(f.actor,mappings());
        assertThat(f.chargeService().quotedTerms(f.actor,loan)).isEqualTo(before);
        assertThat(f.jdbc.queryForObject("select policy_reference from loan_charge_policy where id=?",String.class,old.id())).isEqualTo(old.policyReference());
        assertThatThrownBy(()->f.jdbc.update("delete from loan_charge_policy where id=?",old.id())).isInstanceOf(org.springframework.dao.DataAccessException.class);
    }
    @Test void mappingsStillRequireActiveAccountsInTheSameInstitution(){
        var bad=mappings();bad.setReceivableAccountId(f.cash);
        assertThatThrownBy(()->f.chargeService().saveAccountMappings(f.actor,bad)).hasMessage("charges.error.account");
        assertThat(f.jdbc.queryForObject("select count(*) from loan_charge_policy where request_key=?",Integer.class,bad.getRequestKey())).isZero();
    }
}
