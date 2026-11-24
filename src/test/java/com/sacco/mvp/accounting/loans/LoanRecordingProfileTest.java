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
    final LoanRecordingRepository records=mock(LoanRecordingRepository.class);
    final LoanRecordingEvidenceService evidence=new LoanRecordingEvidenceService(records,
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
        when(onboarding.loanProfile(actor,client)).thenReturn(new LoanProfile(revision,new BigDecimal("2500000.50"),new BigDecimal("500000.25"),
            new BigDecimal("50000"),"Recorded shop","Retail","Business","Shop sales",""));
    }
    RecordForm form() {
        var f=new RecordForm();f.setClientId(client);f.setProductId(product);f.setPurpose("Buy shop stock");
        f.setApplicationDetails(new ApplicationDetails());return f;
    }
    @Test void recordedProfileReplacesForgedValuesAndOnePurposeSatisfiesTheProductSchema() {
        var f=form();f.setIncome(new BigDecimal("999999999.00"));f.setOtherDebt(new BigDecimal("1.00"));f.setExpenses(new BigDecimal("999999999.00"));
        evidence.applyClientProfile(actor,f);evidence.prepare(actor,f,List.of());
        assertThat(f.getIncome()).isEqualByComparingTo("2500000.50");assertThat(f.getOtherDebt()).isNull();assertThat(f.getExpenses()).isNull();
        assertThat(f.getApplicationDetails().getClientProfile().revisionId()).isEqualTo(revision);
        assertThat(f.getApplicationDetails().getFields()).isEmpty();assertThat(f.getBusiness()).isEqualTo("Recorded shop / Retail");
        assertThat(evidence.product(actor,product).fields()).extracting("name").containsExactly("projectLocation");
    }
    @Test void missingProfileFiguresStayUnknownAndFailRequiredAssessment() {
        when(onboarding.loanProfile(actor,client)).thenReturn(new LoanProfile(null,null,null,null,"","","","",""));
        var f=form();evidence.applyClientProfile(actor,f);
        assertThat(f.getIncome()).isNull();assertThat(f.getOtherDebt()).isNull();assertThat(f.getExpenses()).isNull();
        assertThatThrownBy(()->evidence.prepare(actor,f,List.of())).isInstanceOfSatisfying(
            LoanRecordingEvidenceService.FieldValidation.class,error->{assertThat(error.field()).isEqualTo("clientId");
                assertThat(error).hasMessage("recording.error.clientCashFlow");});
    }

    @Test void employmentUsesTheEmployerRecordedInTheProfile() {
        when(onboarding.loanProfile(actor,client)).thenReturn(new LoanProfile(revision,new BigDecimal("2000000"),BigDecimal.ZERO,
            BigDecimal.ZERO,"","","Employment","Salary","Recorded employer"));
        var f=form();evidence.applyClientProfile(actor,f);evidence.prepare(actor,f,List.of());
        assertThat(f.getBusiness()).isEqualTo("Recorded employer");assertThat(f.getExpenses()).isNull();
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
    @Test void olderProfileSnapshotsDoNotAcquireAnEmptyExpenseField() {
        var json=JsonMapper.builder().build();
        var legacy="""
            {"monthlyIncome":1000.00,"monthlyDebtRepayments":0,"businessName":"Shop","businessType":"Retail",
             "activity":"Business","incomeSource":"Sales","employerName":""}
            """;
        var profile=json.readValue(legacy,LoanProfile.class);
        assertThat(profile.monthlyExpenses()).isNull();
        assertThat(profile.monthlyIncome()).isEqualByComparingTo("1000.00");
        assertThat(json.readTree(json.writeValueAsString(profile)).has("monthlyExpenses")).isFalse();
        var historicalProfile=json.readValue(legacy,com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.Profile.class);
        assertThat(historicalProfile.getMonthlyIncome()).isEqualByComparingTo("1000.00");
    }
    @Test void removedProfileQuestionsCannotBePostedAsDynamicOverrides() {
        var f=form();evidence.applyClientProfile(actor,f);f.getApplicationDetails().getFields().put("incomeSource","Forged source");
        assertThatThrownBy(()->evidence.prepare(actor,f,List.of())).hasMessage("applications.error.details");
    }

    @Test void guarantorNamesNeedNoClientRecordAndSurviveJsonRoundTrip() {
        var f=form();f.setLoanMode("CUSTOM");
        var g=new com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.Guarantor();
        g.setFullName("  Asha Example  ");g.setPhone("255712345678");
        f.getApplicationDetails().getGuarantors().add(g);
        evidence.prepare(actor,f,List.of());
        assertThat(g.getFullName()).isEqualTo("Asha Example");assertThat(g.getClientId()).isNull();
        var json=JsonMapper.builder().build();
        var restored=json.readValue(json.writeValueAsString(f),RecordForm.class);
        assertThat(restored.getApplicationDetails().getGuarantors().getFirst().getFullName()).isEqualTo("Asha Example");
    }
    @Test void incompleteOrInvalidGuarantorNamesReturnAFieldError() {
        for(String name:List.of("", " ", "a".repeat(256), "Asha\nExample")) {
            var f=form();f.setLoanMode("CUSTOM");
            var g=new com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.Guarantor();g.setFullName(name);
            f.getApplicationDetails().getGuarantors().add(g);
            assertThatThrownBy(()->evidence.prepare(actor,f,List.of())).isInstanceOfSatisfying(
                LoanRecordingEvidenceService.FieldValidation.class,error->assertThat(error.field()).isEqualTo("applicationDetails.guarantors[0].fullName"));
        }
    }
    @Test void historicalClientLinkedGuarantorsRemainScopedAndLoadIntoTheNameField() {
        var id=UUID.randomUUID();var g=new com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.Guarantor();g.setClientId(id);
        var f=form();f.setLoanMode("CUSTOM");f.getApplicationDetails().getGuarantors().add(g);
        assertThatThrownBy(()->evidence.prepare(actor,f,List.of())).hasMessage("applications.error.guarantors");
        when(records.evidenceClients(eq(actor),anyCollection())).thenReturn(Map.of(id,"CL-1 - Historical Name"));
        assertThatCode(()->evidence.prepare(actor,f,List.of())).doesNotThrowAnyException();
        var draft=new com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.Form();draft.getGuarantors().add(g);
        var application=UUID.randomUUID();when(applications.form(actor,null,application)).thenReturn(draft);
        assertThat(evidence.draftForm(actor,application).getApplicationDetails().getGuarantors().getFirst().getFullName()).isEqualTo("Historical Name");
        assertThat(g.getClientId()).isEqualTo(id);
    }
    @Test void clientApplicationDraftsKeepUnregisteredGuarantorNames() {
        var repository=mock(com.sacco.mvp.clients.applications.repository.ClientApplicationRepository.class);
        var workflow=mock(LoanWorkflowService.class);var clock=mock(ApplicationClock.class);
        var transactions=mock(org.springframework.transaction.PlatformTransactionManager.class);
        when(transactions.getTransaction(any())).thenReturn(mock(org.springframework.transaction.TransactionStatus.class));
        var service=new ClientApplicationService(repository,onboarding,schemas,workflow,requirements,mock(LoanAttachmentService.class),
            mock(FinancialDetailsService.class),clock,JsonMapper.builder().build(),transactions,mock(AuditService.class));
        when(actor.isStaffSession()).thenReturn(true);when(clock.today()).thenReturn(java.time.LocalDate.of(2026,11,12));
        var draft=new com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.Form();draft.setClientId(client);draft.setProductId(product);
        draft.setApplicationDate(clock.today());draft.setAmount(new BigDecimal("1000.00"));draft.setTenureValue(6);
        for(String name:List.of("Asha Example","Juma Example")) {
            var g=new com.sacco.mvp.clients.applications.dto.ClientApplicationDtos.Guarantor();g.setFullName(name);draft.getGuarantors().add(g);
        }
        var id=UUID.randomUUID();when(workflow.saveRecordingDraft(any(),any(),any(),any(),any(),any(),any())).thenReturn(id);
        assertThat(service.save(actor,draft,List.of())).isEqualTo(id);
        verify(repository).command(eq(actor),eq(id),eq(client),eq(1),eq(draft.getRequestKey()),anyString(),contains("Asha Example"),any());
        assertThat(draft.getGuarantors()).hasSize(2);
    }
}
