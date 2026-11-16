package com.sacco.mvp.accounting.loans;

import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.security.access.AccessDeniedException;
import java.math.BigDecimal;
import java.util.*;
import java.util.concurrent.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_(recording|flexible_repayment_[a-f0-9]{8})_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class RecordedLoanEditPostgresTest {
    final LoanRecordingPostgresTest fixture=new LoanRecordingPostgresTest();
    @BeforeAll void start(){fixture.start();}
    @BeforeEach void setup(){fixture.fixture();}
    @AfterAll void stop(){fixture.stop();}
    @Test void revisesTermsAndChargesWithTheSameIdentityAndPermanentBeforeEvidence(){
        fixture.chargePolicy("SEPARATE");var id=fixture.chargedLoan();var service=fixture.service;var jdbc=fixture.jdbc;
        var number=service.detail(fixture.actor,id).loan().number();
        var previous=jdbc.queryForObject("select row_to_json(l)::text from loan_applications l where id=?",String.class,id);
        var oldTerms=fixture.chargeService().frozen(fixture.actor,id);
        var form=service.editForm(fixture.actor,id);form.setPrincipal(new BigDecimal("1200.00"));form.setPurpose("More stock");form.setMonths(2);form.setTenureUnit("MONTHS");form.setTenureValue(2);
        assertThat(service.edit(fixture.actor,id,form,List.of())).isEqualTo(id);
        var detail=service.detail(fixture.actor,id);assertThat(detail.loan().number()).isEqualTo(number);assertThat(detail.loan().status()).isEqualTo("RECORDED");assertThat(detail.loan().principal()).isEqualByComparingTo("1200.00");assertThat(detail.purpose()).isEqualTo("More stock");
        assertThat(jdbc.queryForObject("select before_snapshot->'loan' = ?::jsonb from loan_record_edit where loan_id=?",Boolean.class,previous,id)).isTrue();
        assertThat(jdbc.queryForObject("select jsonb_array_length(before_snapshot->'chargeTerms') from loan_record_edit where loan_id=?",Integer.class,id)).isEqualTo(oldTerms.terms().size());
        assertThat(fixture.chargeService().frozen(fixture.actor,id).total()).isGreaterThan(oldTerms.total());
        assertThat(jdbc.queryForObject("select count(*) from accountant_loan_post where loan_id=?",Integer.class,id)).isZero();
        assertThatThrownBy(()->jdbc.update("delete from loan_record_edit where loan_id=?",id)).hasMessageContaining("append-only");
        assertThatThrownBy(()->jdbc.update("update loan_applications set amount=1 where id=?",id)).hasMessageContaining("immutable");
    }
    @Test void requiresRecorderBranchAndCurrentVersionAndClosesAtFirstReview(){
        var service=fixture.service;var id=service.save(fixture.actor,fixture.form());
        assertThatThrownBy(()->service.editForm(fixture.operator("B1"),id)).isInstanceOf(AccessDeniedException.class);
        assertThatThrownBy(()->service.editForm(fixture.operator("B2"),id)).isInstanceOf(AccessDeniedException.class);
        var old=service.editForm(fixture.actor,id);var current=service.editForm(fixture.actor,id);current.setPurpose("Current change");service.edit(fixture.actor,id,current,List.of());
        assertThatThrownBy(()->service.edit(fixture.actor,id,old,List.of())).hasMessage("loanGroups.error.changed");
        fixture.approve(id);assertThat(service.editable(fixture.actor,id)).isFalse();assertThatThrownBy(()->service.editForm(fixture.actor,id)).isInstanceOf(AccessDeniedException.class);
    }
    @Test void duplicateRequestsAreIdempotentAndCompetingEditsCannotOverwriteEachOther() throws Exception {
        var service=fixture.service;var id=service.save(fixture.actor,fixture.form());var first=service.editForm(fixture.actor,id);var second=service.editForm(fixture.actor,id);
        first.setPurpose("First change");second.setPurpose("Second change");
        var mapper=fixture.context.getBean(tools.jackson.databind.ObjectMapper.class);
        var originalFirst=mapper.convertValue(first,com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.RecordForm.class);
        var originalSecond=mapper.convertValue(second,com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.RecordForm.class);
        try(var pool=Executors.newFixedThreadPool(2)){
            var results=pool.invokeAll(List.<Callable<Boolean>>of(()->{try{service.edit(fixture.actor,id,first,List.of());return true;}catch(IllegalArgumentException e){return false;}},()->{try{service.edit(fixture.actor,id,second,List.of());return true;}catch(IllegalArgumentException e){return false;}}));
            assertThat(results.stream().filter(result->{try{return result.get();}catch(Exception e){throw new RuntimeException(e);}}).count()).isEqualTo(1);
        }
        var winner=service.detail(fixture.actor,id).purpose().equals(first.getPurpose())?originalFirst:originalSecond;
        // A browser retry contains the originally submitted fields, before server normalization.
        assertThat(service.edit(fixture.actor,id,winner,List.of())).isEqualTo(id);
        assertThat(fixture.jdbc.queryForObject("select count(*) from loan_record_edit where loan_id=?",Integer.class,id)).isEqualTo(1);
    }
    @Test void invalidEditsRollBackTheirRevisionAndKeepOriginalTerms(){
        var service=fixture.service;var id=service.save(fixture.actor,fixture.form());var form=service.editForm(fixture.actor,id);form.setPrincipal(new BigDecimal("-1.00"));
        assertThatThrownBy(()->service.edit(fixture.actor,id,form,List.of())).isInstanceOf(IllegalArgumentException.class);
        assertThat(service.detail(fixture.actor,id).loan().principal()).isEqualByComparingTo("1000.00");
        assertThat(fixture.jdbc.queryForObject("select count(*) from loan_record_edit where loan_id=?",Integer.class,id)).isZero();
    }
    @Test void priorSignedConsentRemainsEvidenceButCannotApproveARevisedApplication(){
        fixture.jdbc.update("update loan_product_settings set guarantors_required=1,applicant_attachment_required=true where id=?",fixture.product);
        var service=fixture.service;var id=service.save(fixture.actor,fixture.form());
        fixture.jdbc.update("update loan_applications set attachments_json=?::jsonb where id=?","[{\"id\":\""+UUID.randomUUID()+"\",\"originalName\":\"consent.pdf\",\"documentPurpose\":\"GUARANTOR_CONSENT\"}]",id);
        var reviewTransaction=new org.springframework.transaction.support.TransactionTemplate(fixture.context.getBean(org.springframework.transaction.PlatformTransactionManager.class));
        reviewTransaction.executeWithoutResult(tx->service.validateStaffReview(fixture.actor,id));
        var form=service.editForm(fixture.actor,id);form.setPrincipal(new BigDecimal("1100.00"));service.edit(fixture.actor,id,form,List.of());
        assertThat(service.attachments(fixture.actor,id).getFirst().get("superseded")).isEqualTo(true);
        assertThatThrownBy(()->reviewTransaction.executeWithoutResult(tx->service.validateStaffReview(fixture.actor,id))).hasMessage("recording.error.guarantorConsent");
    }
}
