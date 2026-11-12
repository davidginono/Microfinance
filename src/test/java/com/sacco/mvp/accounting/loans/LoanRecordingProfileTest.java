package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.loans.repository.LoanRecordingRepository;
import com.sacco.mvp.accounting.loans.service.LoanRecordingEvidenceService;
import com.sacco.mvp.accounting.service.GeneralLedgerService;
import com.sacco.mvp.clients.applications.service.ClientApplicationService;
import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.LoanProfile;
import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.repository.LoanProductSettingRepository;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import tools.jackson.databind.json.JsonMapper;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanRecordingProfileTest {
    final AppUserPrincipal actor=mock(AppUserPrincipal.class);
    final OnboardingService onboarding=mock(OnboardingService.class);
    final ClientApplicationService applications=mock(ClientApplicationService.class);
    final LoanProductSettingRepository products=mock(LoanProductSettingRepository.class);
    final LoanProductRequiredAttachmentService requirements=mock(LoanProductRequiredAttachmentService.class);
    final FormSchemaService schemas=new FormSchemaService(products,JsonMapper.builder().build());
    final LoanRecordingEvidenceService evidence=new LoanRecordingEvidenceService(mock(LoanRecordingRepository.class),
        mock(GeneralLedgerService.class),schemas,requirements,applications,onboarding);
    final UUID product=UUID.randomUUID(),client=UUID.randomUUID(),revision=UUID.randomUUID();
    final String schema="""
        {"type":"object","required":["monthlyIncome","monthlyExpenses","otherDebtRepayments",
          "businessOrEmployerName","businessOrEmploymentType","incomeSource","loanPurpose"],"properties":{
          "monthlyIncome":{"type":"number","minimum":0},"monthlyExpenses":{"type":"number","minimum":0},
          "otherDebtRepayments":{"type":"number","minimum":0},"businessOrEmployerName":{"type":"string","minLength":2},
          "businessOrEmploymentType":{"type":"string","enum":["Business","Employment","Other"]},
          "incomeSource":{"type":"string","minLength":1},"loanPurpose":{"type":"string","minLength":5},
          "projectLocation":{"type":"string"}}}
        """;
    @BeforeEach void setup() {
        when(actor.getSaccoId()).thenReturn("institution");
        var p=LoanProductSetting.builder().id(product).saccoId("institution").loanType(LoanType.DEVELOPMENT_LOAN)
            .active(true).guarantorsRequired(0).formSchema(schema).build();
        when(products.findByIdAndSaccoIdAndActiveTrue(product,"institution")).thenReturn(Optional.of(p));
        when(requirements.activeForProduct(product)).thenReturn(List.of());
        when(onboarding.loanProfile(actor,client)).thenReturn(new LoanProfile(revision,new BigDecimal("2500000.50"),
            BigDecimal.ZERO,"Recorded shop","Retail","Business","Shop sales",""));
    }
    RecordForm form() {
        var f=new RecordForm();f.setClientId(client);f.setProductId(product);f.setPurpose("Buy shop stock");
        f.setExpenses(new BigDecimal("500000.00"));f.setApplicationDetails(new ApplicationDetails());return f;
    }
    @Test void recordedProfileReplacesForgedValuesAndOnePurposeSatisfiesTheProductSchema() {
        var f=form();f.setIncome(new BigDecimal("999999999.00"));f.setOtherDebt(new BigDecimal("1.00"));
        evidence.applyClientProfile(actor,f);evidence.prepare(actor,f,List.of());
        assertThat(f.getIncome()).isEqualByComparingTo("2500000.50");assertThat(f.getOtherDebt()).isZero();
        assertThat(f.getApplicationDetails().getClientProfile().revisionId()).isEqualTo(revision);
        assertThat(f.getApplicationDetails().getFields()).isEmpty();assertThat(f.getBusiness()).isEqualTo("Recorded shop / Retail");
        assertThat(evidence.product(actor,product).fields()).extracting("name").containsExactly("projectLocation");
    }
    @Test void missingProfileFiguresStayUnknownAndFailRequiredAssessment() {
        when(onboarding.loanProfile(actor,client)).thenReturn(new LoanProfile(null,null,null,"","","","",""));
        var f=form();evidence.applyClientProfile(actor,f);
        assertThat(f.getIncome()).isNull();assertThat(f.getOtherDebt()).isNull();
        assertThatThrownBy(()->evidence.prepare(actor,f,List.of())).isInstanceOfSatisfying(
            LoanRecordingEvidenceService.FieldValidation.class,error->{assertThat(error.field()).isEqualTo("clientId");
                assertThat(error).hasMessage("recording.error.clientCashFlow");});
    }
    @Test void employmentUsesTheEmployerRecordedInTheProfile() {
        when(onboarding.loanProfile(actor,client)).thenReturn(new LoanProfile(revision,new BigDecimal("2000000"),
            BigDecimal.ZERO,"","","Employment","Salary","Recorded employer"));
        var f=form();evidence.applyClientProfile(actor,f);evidence.prepare(actor,f,List.of());
        assertThat(f.getBusiness()).isEqualTo("Recorded employer");
        assertThat(f.getApplicationDetails().getClientProfile().employerName()).isEqualTo("Recorded employer");
    }
    @Test void savingDraftDoesNotApplyAnOldDocumentedLabelToTheProfileFigures() {
        var previous=new com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.Form();
        var legacy=new RecordForm();legacy.setInformationSource("DOCUMENTED");previous.setRecordingDraft(legacy);
        var f=form();f.setApplicationId(UUID.randomUUID());when(applications.recorded(actor,f.getApplicationId())).thenReturn(previous);
        evidence.saveApplication(actor,f,List.of());
        assertThat(f.getInformationSource()).isEqualTo("DECLARED");
        assertThat(previous.getRecordingDraft().getInformationSource()).isEqualTo("DOCUMENTED");
    }
    @Test void removedProfileQuestionsCannotBePostedAsDynamicOverrides() {
        var f=form();evidence.applyClientProfile(actor,f);f.getApplicationDetails().getFields().put("incomeSource","Forged source");
        assertThatThrownBy(()->evidence.prepare(actor,f,List.of())).hasMessage("applications.error.details");
    }
}
