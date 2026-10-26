package com.sacco.mvp.config;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.controller.GroupOrganizationController;
import com.sacco.mvp.grouplending.dto.GroupCycleDtos.*;
import com.sacco.mvp.grouplending.service.GroupOrganizationService;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,GroupOrganizationMvcTest.Config.class})
class GroupOrganizationMvcTest {
    @Autowired WebApplicationContext context;@Autowired GroupOrganizationService service;@Autowired com.sacco.mvp.grouplending.service.GroupOrganizationDocumentService documents;
    MockMvc mvc;AppUserPrincipal actor;UUID cycle=UUID.randomUUID(),authority=UUID.randomUUID(),client=UUID.randomUUID();
    String path(){return "/finance/groups/cycles/"+cycle+"/organization";}
    @BeforeEach void setup(){reset(service,documents);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),true);}
    void editor(){when(service.workspace(eq(actor),eq(cycle),any(CycleForm.class))).thenAnswer(i->new GroupOrganizationService.Workspace(null,null,null,null,i.getArgument(2),true));}
    @Test void submissionRequiresCsrfAndCannotBindPersonalBorrowerOrTenantFields()throws Exception{
        mvc.perform(post(path()).with(user(actor))).andExpect(status().isForbidden());verifyNoInteractions(service);
        when(service.submit(eq(actor),eq(cycle),any())).thenReturn(UUID.randomUUID());
        mvc.perform(post(path()).with(user(actor)).with(csrf()).param("action","submit").param("organization.authorityId",authority.toString()).param("organization.representativeId",client.toString()).param("organization.principal","1000.23").param("saccoId","FOREIGN").param("stationId","OTHER").param("borrowerPartyId",client.toString()).param("clientId",client.toString()).param("participants[0].clientId",client.toString()))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path()));
        verify(service).submit(eq(actor),eq(cycle),argThat(f->f.getParticipants().isEmpty() && f.getOrganization().getAuthorityId().equals(authority) && f.getOrganization().getRepresentativeId().equals(client) && f.getOrganization().getPrincipal().toPlainString().equals("1000.23")));
    }
    @Test void invalidAmountsRetainEvidenceAndShowLocalizedFieldErrors()throws Exception{
        editor();var r=mvc.perform(post(path()).with(user(actor)).with(csrf()).param("organization.principal","bad-money").param("organization.assessmentEvidence","Retained organization source"))
            .andExpect(status().isOk()).andExpect(view().name("groups/organization")).andExpect(model().attributeHasFieldErrors("cycleForm","organization.principal")).andReturn();
        var errors=(org.springframework.validation.BindingResult)r.getModelAndView().getModel().get(org.springframework.validation.BindingResult.MODEL_KEY_PREFIX+"cycleForm");
        assertThat(errors.getFieldError("organization.principal").getRejectedValue()).isEqualTo("bad-money");
        assertThat(context.getMessage(errors.getFieldError("organization.principal"),Locale.ENGLISH)).isEqualTo("Enter a valid amount.");
        assertThat(context.getMessage(errors.getFieldError("organization.principal"),Locale.of("sw"))).isEqualTo("Weka kiasi sahihi.");
        verify(service,never()).save(any(),any(),any());verify(service).workspace(eq(actor),eq(cycle),argThat(f->f.getOrganization().getAssessmentEvidence().equals("Retained organization source")));
    }
    @Test void staleApplicationKeepsTheSubmittedValuesAndDoesNotRedirect()throws Exception{
        editor();doThrow(new IllegalArgumentException("group.error.changed")).when(service).submit(any(),any(),any());
        mvc.perform(post(path()).with(user(actor)).with(csrf()).param("action","submit").param("purpose","Retained purpose"))
            .andExpect(status().isOk()).andExpect(view().name("groups/organization")).andExpect(model().attributeHasErrors("cycleForm"));
        verify(service).workspace(eq(actor),eq(cycle),argThat(f->f.getPurpose().equals("Retained purpose")));
    }
    @Test void authorizationFailureCannotReturnOrganizationPayoutDetails()throws Exception{
        doThrow(new AccessDeniedException("Synthetic revoked authority view")).when(service).workspace(eq(actor),eq(cycle),isNull());
        mvc.perform(get(path()).with(user(actor))).andExpect(status().isForbidden());
        doThrow(new AccessDeniedException("Synthetic revoked submit")).when(service).authorize(eq(actor),eq("SUBMIT"));
        mvc.perform(post(path()).with(user(actor)).with(csrf()).param("action","submit")).andExpect(status().isForbidden());verify(service,never()).submit(any(),any(),any());
    }
    @Test void documentUploadUsesThePrincipalAndKeepsCreditDraftFields()throws Exception{
        var file=new org.springframework.mock.web.MockMultipartFile("agreementPdf","approved.pdf","application/pdf","%PDF-fixture".getBytes());
        mvc.perform(multipart(path()).file(file).with(user(actor)).param("action","recordDocument")).andExpect(status().isForbidden());verifyNoInteractions(documents);
        mvc.perform(multipart(path()).file(file).with(user(actor)).with(csrf()).param("action","recordDocument").param("organization.business","Retained draft activity").param("documentReference","Approved reference").param("documentLanguage","SW").param("documentChecked","true").param("institutionName","FOREIGN NAME").param("recordedBy",UUID.randomUUID().toString()))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path()));
        verify(documents).record(eq(actor),eq(cycle),argThat(f->f.getOrganization().getBusiness().equals("Retained draft activity")),argThat(d->d.getDocumentReference().equals("Approved reference")&&d.getDocumentLanguage().equals("SW")&&d.isDocumentChecked()),any());verify(service,never()).submit(any(),any(),any());
    }
    @Test void documentValidationRetainsContactsAndCreditEdits()throws Exception{
        editor();doThrow(new IllegalArgumentException("group.error.documentContacts")).when(documents).record(any(),any(),any(),any(),any());
        mvc.perform(multipart(path()).file(new org.springframework.mock.web.MockMultipartFile("agreementPdf","approved.pdf","application/pdf","%PDF-fixture".getBytes())).with(user(actor)).with(csrf()).param("action","recordDocument").param("organization.business","Kept business").param("contactEmail","office@example.invalid"))
            .andExpect(status().isOk()).andExpect(view().name("groups/organization")).andExpect(model().attributeHasErrors("documentForm"));
        verify(service).workspace(eq(actor),eq(cycle),argThat(f->f.getOrganization().getBusiness().equals("Kept business")));
    }
    @Test void fullTermsDownloadUsesDocumentScope()throws Exception{
        var document=UUID.randomUUID();when(documents.original(actor,cycle,document)).thenThrow(new AccessDeniedException("Synthetic scoped document denial"));mvc.perform(get(path()+"/documents/"+document+"/pdf").with(user(actor))).andExpect(status().isForbidden());
    }
    @Test void approvedTermsUseANativePdfAttachment()throws Exception{
        var document=UUID.randomUUID();var bytes="%PDF-test".getBytes();when(documents.original(actor,cycle,document)).thenReturn(bytes);
        mvc.perform(get(path()+"/documents/"+document+"/pdf").with(user(actor))).andExpect(status().isOk()).andExpect(header().string("Content-Type","application/pdf"))
            .andExpect(header().string("Content-Disposition","attachment; filename=organization-terms.pdf")).andExpect(header().string("Cache-Control","no-store")).andExpect(content().bytes(bytes));
    }
    @Test void unsavedReplacementCannotBeSilentlyDiscardedOrSubmitted()throws Exception{
        editor();for(var action:List.of("save","submit"))mvc.perform(multipart(path()).file(new org.springframework.mock.web.MockMultipartFile("agreementPdf","replacement.pdf","application/pdf","%PDF-fixture".getBytes())).with(user(actor)).with(csrf()).param("action",action).param("organization.business","Retained business").param("documentReference","Unrecorded replacement"))
            .andExpect(status().isOk()).andExpect(view().name("groups/organization")).andExpect(model().attributeHasErrors("documentForm"));
        verify(service,never()).save(any(),any(),any());verify(service,never()).submit(any(),any(),any());verifyNoInteractions(documents);
    }
    @Configuration(proxyBeanMethods=false) @EnableWebMvc static class Config {
        @Bean GroupOrganizationService organizationService(){return mock(GroupOrganizationService.class);}
        @Bean com.sacco.mvp.grouplending.service.GroupOrganizationDocumentService documents(){return mock(com.sacco.mvp.grouplending.service.GroupOrganizationDocumentService.class);}
        @Bean GroupOrganizationController organizationController(GroupOrganizationService service,com.sacco.mvp.grouplending.service.GroupOrganizationDocumentService documents){return new GroupOrganizationController(service,documents);}
        @Bean ResourceBundleMessageSource messageSource(){var m=new ResourceBundleMessageSource();m.setBasename("messages");m.setDefaultEncoding("UTF-8");return m;}
    }
}
