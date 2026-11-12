package com.sacco.mvp.config;

import com.sacco.mvp.accounting.loans.controller.LoanRecordingController;
import com.sacco.mvp.accounting.loans.service.*;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import org.springframework.mock.web.MockMultipartFile;
import java.math.BigDecimal;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,LoanRecordingMvcTest.Config.class})
class LoanRecordingMvcTest {
    @Autowired WebApplicationContext context;
    @Autowired LoanRecordingService service;
    @Autowired com.sacco.mvp.clients.applications.service.ClientApplicationService applications;
    @Autowired com.sacco.mvp.grouplending.service.SimpleGroupLoanService simpleGroups;
    MockMvc mvc;AppUserPrincipal actor;
    @BeforeEach void setup(){reset(service,applications,simpleGroups);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("SYNTHETIC").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class));}
    @Test void legacyStaffPagesRedirectToTheAccountantWorkspaceForBothClaimSets() throws Exception {
        var onboardingOnly=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.of(UserClaim.CLIENT_ONBOARDING_VIEW,UserClaim.CLIENT_ONBOARDING_UPDATE));
        var client=UUID.randomUUID();var application=UUID.randomUUID();
        for(var staff:List.of(actor,onboardingOnly))for(var root:List.of("/finance/client-applications","/finance/loan-records/applications")){
            mvc.perform(get(root).with(user(staff))).andExpect(root.contains("client-applications")?status().isNotFound():status().isBadRequest());
            mvc.perform(get(root+"/new").param("clientId",client.toString()).with(user(staff))).andExpect(status().isBadRequest());
            mvc.perform(get(root+"/"+application+"/edit").with(user(staff))).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/finance/loan-records/new?applicationId="+application));
        }
        verify(applications,never()).form(any(),any(),any());
        verify(applications,times(2)).details(actor,application);verify(applications,times(2)).details(onboardingOnly,application);
    }
    @Test void accountantRoutesRejectMissingClaimsInsteadOfOpeningAnotherForm() throws Exception {
        var restricted=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.of(UserClaim.CLIENT_ONBOARDING_VIEW,UserClaim.CLIENT_ONBOARDING_UPDATE));
        doThrow(new org.springframework.security.access.AccessDeniedException("Missing loan recording permission")).when(service).authorize(restricted,"CREATE");
        when(service.list(eq(restricted),any())).thenThrow(new org.springframework.security.access.AccessDeniedException("Missing loan recording permission"));
        mvc.perform(get("/finance/loan-records/new").with(user(restricted))).andExpect(status().isForbidden());
        mvc.perform(get("/finance/loan-records").with(user(restricted))).andExpect(status().isForbidden());
        verifyNoInteractions(applications);
    }
    @Test void cachedStaffFormCannotWriteThroughTheRetiredSaveEndpoint() throws Exception {
        for(var root:List.of("/finance/client-applications","/finance/loan-records/applications")){
            mvc.perform(multipart(root+"/save").with(user(actor))).andExpect(status().isForbidden());
            mvc.perform(multipart(root+"/save").with(user(actor)).with(csrf())).andExpect(status().isMethodNotAllowed());
        }
        verify(applications,never()).save(any(),any(),any());
    }

    @Test void quoteRequiresCsrfAndDoesNotAcceptAFirstDueDateOrRequestedPrincipal() throws Exception {
        when(service.interestQuote(any(),any())).thenReturn(new LoanRecordingService.Quote("Customised loan",new BigDecimal("0.12"),"FLAT_RATE","MONTHLY",new BigDecimal("176.67"),new BigDecimal("60.00"),List.of(),"MONTHS",6));
        mvc.perform(post("/finance/loan-recording/quote").with(user(actor))).andExpect(status().isForbidden());
        mvc.perform(post("/finance/loan-recording/quote").with(user(actor)).with(csrf()).param("loanMode","CUSTOM").param("principal","1000.00").param("tenureUnit","MONTHS").param("tenureValue","6").param("interestBasis","ANNUAL").param("interestPercent","12").param("customMethod","REDUCING_BALANCE").param("requestedPrincipal","9999").param("firstPaymentDate","2026-12-01"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.interest").value("60.00"));
        verify(service).interestQuote(eq(actor),argThat(f->f.getRequestedPrincipal()==null && f.getFirstPaymentDate()==null && Integer.valueOf(6).equals(f.getTenureValue()) && "ANNUAL".equals(f.getInterestBasis()) && f.getCustomMethod()==InterestMethod.REDUCING_BALANCE));
    }
    @Test void chargeQuoteBindsTheEditedRateAndReturnsOnlyChargeValues() throws Exception {
        when(service.interestQuote(any(),any())).thenReturn(new LoanRecordingService.Quote("Customised loan",new BigDecimal("0.12"),"FLAT_RATE","MONTHLY",new BigDecimal("176.67"),new BigDecimal("60.00"),List.of(new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ScheduleRow(1,null,new BigDecimal("1000.00"),new BigDecimal("60.00"),new BigDecimal("1060.00"),new BigDecimal("1060.00"),new BigDecimal("0.00"))),"MONTHS",6));
        var term=new com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Term(UUID.randomUUID(),"PROCESSING","PRINCIPAL_RATE",new BigDecimal("0.0075"),new BigDecimal("7.50"),com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Timing.SEPARATE,UUID.randomUUID(),UUID.randomUUID(),"Recorded charge entry","","");
        when(service.chargeQuote(any(),any())).thenReturn(new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ChargeQuote(new com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Plan(UUID.randomUUID(),"Policy","Agreement",List.of(term),true,""),List.of(new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ChargeField("APPLICATION","","0.00"),new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ChargeField("PROCESSING","0.75","7.50"),new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ChargeField("INSURANCE","0","0.00"))));
        mvc.perform(post("/finance/loan-recording/quote").with(user(actor)).with(csrf()).param("loanMode","CUSTOM").param("principal","1000").param("customProcessingBasis","PERCENTAGE").param("customProcessingPercent","0.75").param("customProcessingCharge","999").param("customInsuranceBasis","AMOUNT").param("insurancePremium","5").param("insuranceEvidence","Removed field"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.schedule.length()").value(1)).andExpect(jsonPath("$.schedule[0].endingBalance").value("0.00")).andExpect(jsonPath("$.charges.terms.length()").value(3)).andExpect(jsonPath("$.charges.terms[1].code").value("PROCESSING"))
            .andExpect(jsonPath("$.charges.terms[1].percentage").value("0.75")).andExpect(jsonPath("$.charges.terms[1].amount").value("7.50"))
            .andExpect(jsonPath("$.charges.total").doesNotExist()).andExpect(jsonPath("$.charges.deductions").doesNotExist()).andExpect(jsonPath("$.charges.policyReference").doesNotExist());
        verify(service).chargeQuote(eq(actor),argThat(f->"PERCENTAGE".equals(f.getCustomProcessingBasis()) && new BigDecimal("0.75").equals(f.getCustomProcessingPercent()) && f.getInsuranceEvidence()==null));
        verify(service,never()).topUpQuote(any(),any(),any(),any());
    }
    @Test void interestResponsePreservesCentsBeyondTheJavascriptIntegerRange() throws Exception {
        var payment=new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ScheduleRow(1,null,new BigDecimal("1.00"),new BigDecimal("9007199254740991.01"),new BigDecimal("9007199254740992.01"),new BigDecimal("9007199254740992.01"),new BigDecimal("0.00"));
        when(service.interestQuote(any(),any())).thenReturn(new LoanRecordingService.Quote("Customised loan",BigDecimal.ZERO,"FLAT_RATE","MONTHLY",payment.amount(),payment.interest(),List.of(payment),"MONTHS",1));
        mvc.perform(post("/finance/loan-recording/quote").with(user(actor)).with(csrf()).param("principal","1000.00"))
            .andExpect(status().isOk()).andExpect(jsonPath("$.interest").value("9007199254740991.01"))
            .andExpect(jsonPath("$.schedule[0].number").value(1)).andExpect(jsonPath("$.schedule[0].interest").value("9007199254740991.01"))
            .andExpect(jsonPath("$.schedule[0].amount").value("9007199254740992.01"))
            .andExpect(jsonPath("$.schedule[0].beginningBalance").value("9007199254740992.01")).andExpect(jsonPath("$.schedule[0].endingBalance").value("0.00"));
    }
    @Test void multipartRecordingPassesDocumentsAndRetainsValuesOnInvalidInput() throws Exception {
        var id=UUID.randomUUID();when(service.save(any(),any(),any())).thenReturn(id);var file=new MockMultipartFile("attachments","proof.pdf","application/pdf","%PDF synthetic".getBytes());
        mvc.perform(multipart("/finance/loan-records").file(file).with(user(actor)).with(csrf()).param("principal","1000.00").param("loanMode","CUSTOM").param("tenureValue","10").param("tenureUnit","DAYS"))
            .andExpect(redirectedUrl("/finance/loan-records/"+id));verify(service).save(eq(actor),argThat(f->Integer.valueOf(10).equals(f.getTenureValue())),argThat(files->files.size()==1 && "proof.pdf".equals(files.getFirst().getOriginalFilename())));
        mvc.perform(multipart("/finance/loan-records").with(user(actor)).with(csrf()).param("principal","invalid").param("purpose","Business stock"))
            .andExpect(view().name("accounting/loans/record-form")).andExpect(model().attributeHasFieldErrors("recordForm","principal"));
    }
    @Test void productChoicesReturnExactLimitsAndTenureUnitWithoutAnotherLookup() throws Exception {
        var id=UUID.randomUUID();
        when(service.choices(actor,"products","",0)).thenReturn(List.of(new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice(id,"Day loan","","DAYS","1000.00","9007199254740991.01",7,30)));
        mvc.perform(get("/finance/loan-recording/choices/products").with(user(actor)))
            .andExpect(status().isOk()).andExpect(jsonPath("$[0].id").value(id.toString()))
            .andExpect(jsonPath("$[0].tenureUnit").value("DAYS"))
            .andExpect(jsonPath("$[0].minimumAmount").value("1000.00")).andExpect(jsonPath("$[0].maximumAmount").value("9007199254740991.01"))
            .andExpect(jsonPath("$[0].minimumTenure").value(7)).andExpect(jsonPath("$[0].maximumTenure").value(30));
    }
    @Test void invalidRecordingRetainsSelectedProductLimitsOutsideTheFirstLookupPage() throws Exception {
        var id=UUID.randomUUID();var product=new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice(id,"Day loan","","DAYS","500.00","5000.00",7,30);
        when(service.recordProduct(actor,id)).thenReturn(product);
        mvc.perform(multipart("/finance/loan-records").with(user(actor)).with(csrf()).param("loanMode","CONFIGURED").param("productId",id.toString()).param("principal","invalid"))
            .andExpect(view().name("accounting/loans/record-form")).andExpect(model().attributeHasFieldErrors("recordForm","principal"))
            .andExpect(model().attribute("selectedProduct",product));
    }
    @Test void unavailableProductRetainsEnteredValuesAndOriginalValidationErrors() throws Exception {
        var id=UUID.randomUUID(); var officerId=UUID.randomUUID();
        var evidence=(com.sacco.mvp.accounting.loans.service.LoanRecordingEvidenceService)org.springframework.test.util.ReflectionTestUtils.getField(context.getBean(LoanRecordingController.class),"evidence");
        when(evidence.product(actor,id)).thenThrow(new IllegalArgumentException("applications.error.product"));
        mvc.perform(multipart("/finance/loan-records").with(user(actor)).with(csrf())
            .param("loanMode","CONFIGURED").param("productId",id.toString()).param("principal","invalid").param("purpose","Retained stock purchase")
            .param("applicationDetails.loanOfficerId",officerId.toString())
            .param("business","Old business field").param("informationSource","DOCUMENTED")
            .param("notes","Old description").param("applicationDetails.useOfFunds","Old use of funds"))
            .andExpect(status().isOk()).andExpect(view().name("accounting/loans/record-form"))
            .andExpect(model().attributeHasFieldErrors("recordForm","principal"))
            .andExpect(model().attribute("recordForm",org.hamcrest.Matchers.hasProperty("applicationDetails",
                org.hamcrest.Matchers.hasProperty("loanOfficerId",org.hamcrest.Matchers.nullValue()))))
            .andExpect(model().attribute("recordForm",org.hamcrest.Matchers.hasProperty("applicationDetails",
                org.hamcrest.Matchers.hasProperty("useOfFunds",org.hamcrest.Matchers.is("")))))
            .andExpect(model().attribute("recordForm",org.hamcrest.Matchers.hasProperty("business",org.hamcrest.Matchers.is(""))))
            .andExpect(model().attribute("recordForm",org.hamcrest.Matchers.hasProperty("informationSource",org.hamcrest.Matchers.is("DECLARED"))))
            .andExpect(model().attribute("recordForm",org.hamcrest.Matchers.hasProperty("notes",org.hamcrest.Matchers.is(""))))
            .andExpect(model().attribute("recordForm",org.hamcrest.Matchers.hasProperty("purpose",org.hamcrest.Matchers.is("Retained stock purchase"))));
    }
    @Test void recordingCannotOverrideCashFlowOrItsProfileSource() throws Exception {
        mvc.perform(multipart("/finance/loan-records").with(user(actor)).with(csrf())
            .param("applicationDetails.collateral[0].itemName","Bicycle")
            .param("applicationDetails.collateral[0].owner","Forged owner")
            .param("applicationDetails.collateral[0].location","Forged location")
            .param("applicationDetails.collateral[0].estimatedValue","999999999")
            .param("principal","invalid").param("income","999999999").param("otherDebt","0").param("expenses","0")
            .param("applicationDetails.clientProfile.revisionId",UUID.randomUUID().toString()))
            .andExpect(view().name("accounting/loans/record-form"))
            .andExpect(model().attribute("recordForm",org.hamcrest.Matchers.hasProperty("income",org.hamcrest.Matchers.nullValue())))
            .andExpect(model().attribute("recordForm",org.hamcrest.Matchers.hasProperty("otherDebt",org.hamcrest.Matchers.nullValue())))
            .andExpect(model().attribute("recordForm",org.hamcrest.Matchers.hasProperty("expenses",org.hamcrest.Matchers.nullValue())))
            .andExpect(model().attribute("recordForm",org.hamcrest.Matchers.hasProperty("applicationDetails",
                org.hamcrest.Matchers.hasProperty("collateral",org.hamcrest.Matchers.hasItem(org.hamcrest.Matchers.allOf(
                    org.hamcrest.Matchers.hasProperty("owner",org.hamcrest.Matchers.is("")),
                    org.hamcrest.Matchers.hasProperty("location",org.hamcrest.Matchers.is("")),
                    org.hamcrest.Matchers.hasProperty("estimatedValue",org.hamcrest.Matchers.nullValue())))))))
            .andExpect(model().attribute("recordForm",org.hamcrest.Matchers.hasProperty("applicationDetails",
                org.hamcrest.Matchers.hasProperty("clientProfile",org.hamcrest.Matchers.nullValue()))));
    }
    @Test void documentDownloadChecksScopeThroughServiceAndPreventsSniffing() throws Exception {
        var id=UUID.randomUUID();when(service.attachment(eq(actor),eq(id),eq("proof"))).thenReturn(new LoanAttachmentService.AttachmentResource("%PDF synthetic".getBytes(),"proof.pdf","application/pdf"));
        mvc.perform(get("/finance/loan-records/"+id+"/attachments/proof").with(user(actor))).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(content().contentType("application/pdf"));
    }
    @Test void obsoleteDisbursementEntryRedirectsToReviewAndCannotPost() throws Exception {
        mvc.perform(get("/finance/loan-disbursements/new").with(user(actor))).andExpect(redirectedUrl("/disbursement/loan-applications"));
        mvc.perform(post("/finance/loan-disbursements").with(user(actor)).with(csrf())).andExpect(status().isGone());
        verify(service).authorize(actor,"VIEW");verify(service,never()).disburse(any(),any(),any());
    }
    @Test void historicalRecordedApplicationsRequireCsrfAndSubmitThroughScopedService() throws Exception {
        var id=UUID.randomUUID();
        mvc.perform(post("/finance/loan-records/"+id+"/submit-for-review").with(user(actor))).andExpect(status().isForbidden());
        var key=UUID.randomUUID();
        mvc.perform(post("/finance/loan-records/"+id+"/submit-for-review").with(user(actor)).with(csrf()).param("requestKey",key.toString())).andExpect(redirectedUrl("/finance/loan-records/"+id+"/review"));
        verify(context.getBean(com.sacco.mvp.accounting.loans.service.LoanGroupApprovalService.class)).submit(actor,id,key);
    }
    @Test void topUpPreviewIsScopedUncacheableAndPreservesDecimalStrings() throws Exception {
        var source=UUID.randomUUID();var date=java.time.LocalDate.of(2026,10,8);
        when(service.topUpQuote(actor,source,date,null)).thenReturn(new com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.TopUpQuote(source,"202600001",UUID.randomUUID(),"Synthetic client",date,new BigDecimal("1000.00"),new BigDecimal("20.00"),new BigDecimal("50.00"),3));
        mvc.perform(get("/finance/loan-recording/top-up-quote").with(user(actor)).param("source",source.toString()).param("date",date.toString()).param("principal","9007199254740992.01"))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(jsonPath("$.principal").value("1000.00")).andExpect(jsonPath("$.cash").value("9007199254739972.01"));
        verify(service).authorize(actor,"CREATE");verify(service).topUpQuote(actor,source,date,null);
    }
    @Test void topUpCorrectionRequiresCsrfAndDelegatesTheLinkedCommand() throws Exception {
        var id=UUID.randomUUID();var key=UUID.randomUUID();
        mvc.perform(post("/finance/loan-records/"+id+"/reverse-top-up").with(user(actor))).andExpect(status().isForbidden());
        mvc.perform(post("/finance/loan-records/"+id+"/reverse-top-up").with(user(actor)).with(csrf()).param("requestKey",key.toString()).param("date","2026-10-08").param("reason","Correct transfer").param("evidence","Synthetic bank evidence"))
            .andExpect(redirectedUrl("/finance/loan-records/"+id));
        verify(service).reverseTopUp(actor,id,key,java.time.LocalDate.of(2026,10,8),"Correct transfer","Synthetic bank evidence");
    }
    @Test void chargeAssessmentRequiresCsrfAndBindsOnlyEvidenceCommand() throws Exception {
        var id=UUID.randomUUID();var key=UUID.randomUUID();var charges=context.getBean(com.sacco.mvp.accounting.charges.service.LoanChargeService.class);reset(charges);
        mvc.perform(post("/finance/loan-records/"+id+"/charges/assess").with(user(actor))).andExpect(status().isForbidden());
        mvc.perform(post("/finance/loan-records/"+id+"/charges/assess").with(user(actor)).with(csrf()).param("requestKey",key.toString()).param("date","2026-09-01").param("evidence","Synthetic signed agreement").param("amount","1.00").param("saccoId","FOREIGN"))
            .andExpect(redirectedUrl("/finance/loan-records/"+id));verify(charges).assess(eq(actor),eq(id),argThat(f->f.getRequestKey().equals(key) && f.getDate().equals(java.time.LocalDate.of(2026,9,1)) && f.getEvidence().equals("Synthetic signed agreement")));
    }
    @Test void chargePolicyUsesTheAdminProductRouteAndSessionScope() throws Exception {
        var product=UUID.randomUUID();var charges=context.getBean(com.sacco.mvp.accounting.charges.service.LoanChargeService.class);reset(charges);
        var admin=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.MINOR_ADMIN)
            .staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.of(UserClaim.LOAN_PRODUCTS_CONFIGURE,UserClaim.ACCOUNTING_ACCOUNTS_UPDATE));
        var path="/admin/settings-controls/loan-products/"+product+"/charge-accounts";
        mvc.perform(post(path).with(user(admin))).andExpect(status().isForbidden());
        doThrow(new org.springframework.security.access.AccessDeniedException("Missing explicit permission")).when(charges).savePolicy(eq(actor),any());
        mvc.perform(post(path).with(user(actor)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(path).with(user(admin)).with(csrf()).param("productId",UUID.randomUUID().toString()).param("effectiveFrom","2026-09-01").param("enabled","true").param("managementTiming","DEDUCT").param("insuranceTiming","SEPARATE").param("saccoId","FOREIGN"))
            .andExpect(redirectedUrl("/admin/settings-controls/loan-products/"+product+"/edit?tab=accounts"));verify(charges).savePolicy(eq(admin),argThat(f->f.getProductId().equals(product) && f.getEffectiveFrom().equals(java.time.LocalDate.of(2026,9,1)) && f.getManagementTiming()==com.sacco.mvp.accounting.charges.dto.LoanChargeDtos.Timing.DEDUCT));
    }
    @Test void chargeMappingErrorsReturnToTheAccountsTabAndKeepAllowedValues() throws Exception {
        var product=UUID.randomUUID();var application=UUID.randomUUID();var processing=UUID.randomUUID();
        var charges=context.getBean(com.sacco.mvp.accounting.charges.service.LoanChargeService.class);reset(charges);
        var admin=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.ICT_MANAGER)
            .staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.of(UserClaim.LOAN_PRODUCTS_CONFIGURE,UserClaim.ACCOUNTING_ACCOUNTS_UPDATE));
        doThrow(new IllegalArgumentException("charges.error.account")).when(charges).savePolicy(eq(admin),any());
        mvc.perform(post("/admin/settings-controls/loan-products/"+product+"/charge-accounts").with(user(admin)).with(csrf())
            .param("applicationAccountId",application.toString()).param("processingAccountId",processing.toString()).param("policyReference","Retain this policy")
            .param("accountingVersion","1").param("managementAccountId",UUID.randomUUID().toString()))
            .andExpect(redirectedUrl("/admin/settings-controls/loan-products/"+product+"/edit?tab=accounts"))
            .andExpect(flash().attribute("chargeError","charges.error.account"));
        verify(charges).savePolicy(eq(admin),argThat(f->f.getProductId().equals(product) && f.getApplicationAccountId().equals(application)
            && f.getProcessingAccountId().equals(processing) && f.getPolicyReference().equals("Retain this policy") && f.getManagementAccountId()==null));
        mvc.perform(get("/admin/settings-controls/loan-products/"+product+"/charge-accounts").with(user(admin)))
            .andExpect(redirectedUrl("/admin/settings-controls/loan-products/"+product+"/edit?tab=accounts"));
        verify(charges).policy(admin,product);
    }
    @Test void recordingBindsGuarantorNamesWithoutAcceptingClientLinks() throws Exception {
        var id=UUID.randomUUID();when(service.save(eq(actor),any(),any())).thenReturn(id);
        mvc.perform(multipart("/finance/loan-records").with(user(actor)).with(csrf())
            .param("applicationDetails.guarantors[0].fullName","Asha Example")
            .param("applicationDetails.guarantors[0].clientId",UUID.randomUUID().toString()))
            .andExpect(redirectedUrl("/finance/loan-records/"+id));
        verify(service).save(eq(actor),argThat(f->f.getApplicationDetails().getGuarantors().getFirst().getFullName().equals("Asha Example")
            && f.getApplicationDetails().getGuarantors().getFirst().getClientId()==null),any());
        mvc.perform(get("/finance/loan-recording/evidence/choices/guarantors").with(user(actor))).andExpect(status().isNotFound());
    }
    @Test void individualIsTheDefaultAndAGroupLoadsItsWholeRoster()throws Exception{
        mvc.perform(get("/finance/loan-records/new").with(user(actor))).andExpect(status().isOk()).andExpect(model().attribute("recordForm",org.hamcrest.Matchers.hasProperty("loanFor",org.hamcrest.Matchers.is("INDIVIDUAL"))));
        UUID group=UUID.randomUUID();var setup=new com.sacco.mvp.grouplending.dto.SimpleGroupLoanDtos.Setup(group,7,"Five clients",UUID.randomUUID(),"Asha","BANK_ACCOUNT","Bank","Group","ACCOUNT","Checked",List.of(new com.sacco.mvp.grouplending.dto.SimpleGroupLoanDtos.Person(UUID.randomUUID(),"C1","Asha"),new com.sacco.mvp.grouplending.dto.SimpleGroupLoanDtos.Person(UUID.randomUUID(),"C2","Juma")));
        when(simpleGroups.setup(actor,group)).thenReturn(setup);
        mvc.perform(get("/finance/loan-records/new").param("groupId",group.toString()).with(user(actor))).andExpect(status().isOk()).andExpect(model().attribute("groupSetup",setup)).andExpect(model().attribute("recordForm",org.hamcrest.Matchers.hasProperty("loanFor",org.hamcrest.Matchers.is("GROUP"))));
    }
    @Test void groupSubmissionBindsEvidenceButIgnoresForgedContractAndGuarantorLinks()throws Exception{
        UUID loan=UUID.randomUUID(),group=UUID.randomUUID(),person=UUID.randomUUID();when(service.saveGroup(eq(actor),any(),anyList(),any())).thenReturn(loan);
        var paper=new MockMultipartFile("groupAgreement","signed.pdf","application/pdf","%PDF fixture".getBytes());
        mvc.perform(multipart("/finance/loan-records").file(paper).with(user(actor)).with(csrf()).param("loanFor","GROUP").param("groupApplication.groupId",group.toString()).param("groupApplication.expectedVersion","7").param("groupApplication.signers[0].clientId",person.toString()).param("groupApplication.signers[0].signedOn","2026-11-12").param("groupApplication.signers[0].incomeSource","Income 1").param("groupApplication.signers[0].evidence","Budget 1").param("groupApplication.checked","true").param("groupAgreementChecksum","forged").param("groupRequestHash","forged").param("applicationDetails.guarantors[0].clientId",person.toString()).param("applicationDetails.guarantors[0].clientNumber","C7"))
            .andExpect(redirectedUrl("/finance/loan-records/"+loan));
        verify(service).saveGroup(eq(actor),argThat(f->f.getGroupAgreementChecksum()==null&&f.getGroupRequestHash()==null&&f.getGroupApplication().getExpectedVersion()==7&&f.getGroupApplication().getSigners().getFirst().getClientId().equals(person)&&f.getApplicationDetails().getGuarantors().getFirst().getClientId()==null),anyList(),eq(paper));
        mvc.perform(multipart("/finance/loan-records").with(user(actor)).param("loanFor","GROUP")).andExpect(status().isForbidden());
    }
    @Test void retiredGroupCommandsHaveNoOperationalHandlers()throws Exception{
        for(var url:List.of("/finance/groups/policies","/finance/groups/policies/new","/finance/groups/"+UUID.randomUUID()+"/authorities","/finance/group-cycles/"+UUID.randomUUID()+"/submit","/finance/group-coverage")){
            mvc.perform(get(url).with(user(actor))).andExpect(status().is4xxClientError());
            mvc.perform(post(url).with(user(actor)).with(csrf())).andExpect(status().is4xxClientError());
        }
        verifyNoInteractions(simpleGroups,service);
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean("access") @Primary AccessControlService access(){return new AccessControlService();}
        @Bean com.sacco.mvp.accounting.charges.service.LoanChargeService charges(){return mock(com.sacco.mvp.accounting.charges.service.LoanChargeService.class);}
        @Bean com.sacco.mvp.accounting.charges.controller.LoanChargeController chargeController(com.sacco.mvp.accounting.charges.service.LoanChargeService charges){return new com.sacco.mvp.accounting.charges.controller.LoanChargeController(charges);}
        @Bean com.sacco.mvp.clients.applications.service.ClientApplicationService applications(){return mock(com.sacco.mvp.clients.applications.service.ClientApplicationService.class);}

        @Bean com.sacco.mvp.clients.applications.controller.ClientApplicationController historyController(com.sacco.mvp.clients.applications.service.ClientApplicationService applications){return new com.sacco.mvp.clients.applications.controller.ClientApplicationController(applications,new org.springframework.context.support.StaticMessageSource());}
        @Bean com.sacco.mvp.grouplending.service.SimpleGroupLoanService simpleGroups(){return mock(com.sacco.mvp.grouplending.service.SimpleGroupLoanService.class);}
        @Bean LoanRecordingService service(){return mock(LoanRecordingService.class);}
        @Bean com.sacco.mvp.accounting.loans.service.LoanGroupApprovalService approvals(){return mock(com.sacco.mvp.accounting.loans.service.LoanGroupApprovalService.class);}
        @Bean LoanRecordingController controller(LoanRecordingService service,com.sacco.mvp.accounting.loans.service.LoanGroupApprovalService approvals,com.sacco.mvp.grouplending.service.SimpleGroupLoanService simpleGroups){return new LoanRecordingController(simpleGroups,service,approvals,mock(LoanRecordingPdfService.class),mock(ApplicationClock.class),new org.springframework.context.support.StaticMessageSource(),mock(SaccoLogoStorageService.class),org.mockito.Mockito.mock(com.sacco.mvp.accounting.loans.service.LoanRecordingEvidenceService.class));}
    }
}
