package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.loans.repository.LoanRecordingRepository;
import com.sacco.mvp.repository.LoanProductRequiredAttachmentRepository;
import com.sacco.mvp.service.LoanProductRequiredAttachmentService;
import com.sacco.mvp.service.dto.LoanProductDocumentsForm;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

@EnabledIfEnvironmentVariable(named="MICROFINANCE_RECORDING_TEST_URL",matches="jdbc:postgresql://127\\.0\\.0\\.1:[0-9]+/microfinance_(recording|flexible_repayment_[a-f0-9]{8})_test")
@TestInstance(TestInstance.Lifecycle.PER_CLASS)
class LoanProductDocumentsPostgresTest {
    final LoanRecordingPostgresTest f=new LoanRecordingPostgresTest();
    TransactionTemplate tx; LoanProductRequiredAttachmentService definitions;
    @BeforeAll void start(){f.start();tx=new TransactionTemplate(f.context.getBean(PlatformTransactionManager.class));definitions=new LoanProductRequiredAttachmentService(f.context.getBean(LoanProductRequiredAttachmentRepository.class));}
    @BeforeEach void setup(){f.fixture();}
    @AfterAll void stop(){f.stop();}
    String review(UUID loan){return f.service.validateStaffReview(f.actor,loan);}
    void policy(boolean required,int guarantors){f.jdbc.update("update loan_product_settings set applicant_attachment_required=?,guarantors_required=? where id=?",required,guarantors,f.product);}
    void files(UUID loan,String json){f.jdbc.update("update loan_applications set attachments_json=?::jsonb where id=?",json,loan);}
    void names(String name,String id){var form=new LoanProductDocumentsForm();form.setRequiredAttachmentName(List.of(name));form.setRequiredAttachmentMaxSizeMb(List.of(new BigDecimal("1.00")));form.setRequiredAttachmentId(List.of(id));tx.executeWithoutResult(s->definitions.replaceForProduct(f.product,form));}
    UUID definition(){return definitions.activeForProduct(f.product).getFirst().getId();}
    void freeze(UUID loan,String policy){tx.executeWithoutResult(s->{var r=new LoanRecordingRepository(f.jdbc);var l=r.groupLoan(f.actor,loan,true);var at=f.context.getBean(com.sacco.mvp.service.ApplicationClock.class).now();var rule=r.groupRule(f.actor,f.product).orElseGet(()->{var created=new GroupRule(UUID.randomUUID(),f.product,1,"[{\"group\":\"ACCOUNTANT\",\"approvals\":1}]");r.groupRule(f.actor,created,at);return created;});r.groupRequest(f.actor,new GroupRequest(UUID.randomUUID(),loan,rule.id(),f.actor.getMemberId(),1,l.termsHash(),l.status()),at,policy);});}
    @Test void optionalAttachmentsPermitReviewWithGuarantorsAndIgnoreRetainedDocumentNames(){
        policy(false,1);names("Signed guarantor papers","");var loan=f.service.save(f.actor,f.form());
        assertThat(review(loan)).contains("\"required\":false","\"guarantorConsent\":false","\"documents\":[]");
        assertThat(f.jdbc.queryForObject("select count(*) from accountant_loan_post where loan_id=?",Integer.class,loan)).isZero();
        assertThatThrownBy(()->f.service.validateStaffReview(f.operator("B2"),loan)).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
    }
    @Test void requiredGuarantorPapersRejectMissingOrSupersededConsent(){
        policy(true,1);var loan=f.service.save(f.actor,f.form());
        assertThatThrownBy(()->review(loan)).hasMessage("recording.error.guarantorConsent");
        files(loan,"[{\"id\":\""+UUID.randomUUID()+"\",\"documentPurpose\":\"GUARANTOR_CONSENT\",\"superseded\":true}]");
        assertThatThrownBy(()->review(loan)).hasMessage("recording.error.guarantorConsent");
        files(loan,"[{\"id\":\""+UUID.randomUUID()+"\",\"documentPurpose\":\"GUARANTOR_CONSENT\"}]");
        assertThat(review(loan)).contains("\"required\":true","\"guarantorConsent\":true");
    }
    @Test void namedRequirementsCheckTheDocumentIdAndConfiguredSize(){
        policy(true,0);names("Signed application","");var requirement=definition();var loan=f.service.save(f.actor,f.form());
        assertThatThrownBy(()->review(loan)).hasMessage("recording.error.requiredAttachments");
        files(loan,"[{\"requiredAttachmentId\":\""+UUID.randomUUID()+"\",\"size\":100}]");
        assertThatThrownBy(()->review(loan)).hasMessage("recording.error.requiredAttachments");
        files(loan,"[{\"requiredAttachmentId\":\""+requirement+"\",\"size\":1048577}]");
        assertThatThrownBy(()->review(loan)).hasMessage("recording.error.requiredAttachments");
        files(loan,"[{\"requiredAttachmentId\":\""+requirement+"\",\"size\":1048576}]");
        assertThat(review(loan)).contains(requirement.toString());
    }
    @Test void reviewFreezesDocumentRulesAndLegacyReviewsKeepTheirConsentGate(){
        policy(false,1);var optional=f.service.save(f.actor,f.form());freeze(optional,review(optional));
        policy(true,1);assertThat(review(optional)).contains("\"required\":false");
        var required=f.service.save(f.actor,f.form());files(required,"[{\"documentPurpose\":\"GUARANTOR_CONSENT\"}]");freeze(required,review(required));
        policy(false,1);files(required,"[]");assertThatThrownBy(()->review(required)).hasMessage("recording.error.guarantorConsent");
        var legacy=f.service.save(f.actor,f.form());freeze(legacy,"{}");assertThatThrownBy(()->review(legacy)).hasMessage("recording.error.guarantorConsent");
        assertThatThrownBy(()->f.jdbc.update("update loan_group_request set document_policy='{}'::jsonb where loan_id=?",required)).hasMessageContaining("append-only");
    }
    @Test void savingNamesKeepsStableIdsAndRetainsRenamedOrRemovedHistory(){
        names("Original papers","");UUID original=definition();names("Original papers",original.toString());assertThat(definition()).isEqualTo(original);
        names("Different papers",original.toString());UUID renamed=definition();assertThat(renamed).isNotEqualTo(original);
        assertThat(f.jdbc.queryForObject("select active from loan_product_required_attachments where id=?",Boolean.class,original)).isFalse();
        assertThatThrownBy(()->names("Foreign papers",UUID.randomUUID().toString())).hasMessage("productDocuments.invalid");assertThat(definition()).isEqualTo(renamed);
        tx.executeWithoutResult(s->definitions.replaceForProduct(f.product,new LoanProductDocumentsForm()));
        assertThat(definitions.activeForProduct(f.product)).isEmpty();
        assertThat(f.jdbc.queryForObject("select count(*) from loan_product_required_attachments where loan_product_setting_id=?",Integer.class,f.product)).isEqualTo(2);
    }
}
