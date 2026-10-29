package com.sacco.mvp.config;

import com.sacco.mvp.clients.onboarding.controller.*;
import com.sacco.mvp.clients.onboarding.dto.OnboardingDtos.*;
import com.sacco.mvp.clients.onboarding.service.OnboardingService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.nio.file.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,ClientOnboardingMvcTest.Config.class})
class ClientOnboardingMvcTest {
    @Autowired WebApplicationContext context;
    @Autowired OnboardingService service;
    MockMvc mvc;AppUserPrincipal staff,client;UUID id=UUID.randomUUID();String root="/finance/clients/onboarding";
    @BeforeEach void setup() {
        reset(service);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        staff=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),true);
        client=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.MEMBER).memberAccount(true).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),false);
        when(service.client(any(),any())).thenReturn(new Client(id,"C1","Synthetic client",null,null,"DRAFT",UUID.randomUUID(),1,null));when(service.latest(any(),any())).thenReturn(Optional.empty());
    }
    @Test void everyStaffWriteRequiresCsrfAndRejectsClientSessions()throws Exception {
        for(String path:List.of(root+"/new",root+"/"+id+"/edit",root+"/"+id+"/review",root+"/"+id+"/documents/new")) {
            mvc.perform(post(path).with(user(staff))).andExpect(status().isForbidden());
            mvc.perform(post(path).with(user(client)).with(csrf())).andExpect(status().isForbidden());
        }
    }
    @Test void submittedProfileCannotBindServerIdentityScopeOrVerification()throws Exception {
        mvc.perform(post(root+"/"+id+"/edit").with(user(staff)).with(csrf()).param("action","SUBMITTED").param("profile.address","Keep this address").param("profile.fullName","Forged name").param("profile.phone","Forged phone").param("saccoId","FOREIGN").param("actorId",client.getMemberId().toString()).param("state","VERIFY"))
            .andExpect(redirectedUrl(root+"/"+id));
        verify(service).save(eq(staff),eq(id),argThat(f->f.getAction().equals("SUBMITTED")&&f.getProfile().getAddress().equals("Keep this address")&&f.getProfile().getFullName().isEmpty()&&f.getProfile().getPhone().isEmpty()));
    }
    @Test void failedProfileKeepsValuesAndReportsSafeError()throws Exception {
        when(service.save(any(),any(),any())).thenThrow(new IllegalArgumentException("kyc.error.documents"));
        var result=mvc.perform(post(root+"/"+id+"/edit").with(user(staff)).with(csrf()).param("profile.address","Keep my values").param("profile.identityNumber","TZ-1234")).andExpect(view().name("onboarding/form")).andExpect(model().attributeHasErrors("profileForm")).andReturn();
        assertThat(((ProfileForm)result.getModelAndView().getModel().get("profileForm")).getProfile().getAddress()).isEqualTo("Keep my values");
    }
    @Test void documentsDownloadAsPrivateAttachmentsAndScopeFailureIsForbidden()throws Exception {
        UUID doc=UUID.randomUUID();when(service.download(staff,id,doc)).thenReturn(new Download(new byte[]{1,2},"application/pdf","evidence.pdf"));
        mvc.perform(get(root+"/"+id+"/documents/"+doc).with(user(staff))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("Content-Security-Policy","sandbox")).andExpect(header().string("X-Content-Type-Options","nosniff")).andExpect(header().string("Content-Disposition","attachment; filename=\"evidence.pdf\""));
        when(service.download(staff,id,doc)).thenThrow(new org.springframework.security.access.AccessDeniedException("Unavailable"));mvc.perform(get(root+"/"+id+"/documents/"+doc).with(user(staff))).andExpect(status().isForbidden());
    }
    @Test void ownStatusUsesAuthenticatedIdentityAndDoesNotReturnStaffEvidence()throws Exception {
        when(service.ownWorkspace(client,0,null)).thenReturn(new OwnWorkspace(new Client(client.getMemberId(),"C1","Synthetic client",null,null,"NOT_STARTED",null,0,null),null,List.of(),null,new Page<>(List.of(),0,false),false));
        mvc.perform(get("/app/onboarding").with(user(client)).param("clientId",staff.getMemberId().toString())).andExpect(view().name("onboarding/status")).andExpect(model().attributeDoesNotExist("revision","documents","decisions"));verify(service).ownWorkspace(client,0,null);
    }
    @Test void clientWritesRequireCsrfAndCannotBindStaffFields()throws Exception {
        mvc.perform(post("/app/onboarding/edit").with(user(client))).andExpect(status().isForbidden());
        mvc.perform(multipart("/app/onboarding/documents/new").file("file",new byte[]{1}).with(user(client))).andExpect(status().isForbidden());
        mvc.perform(post("/app/onboarding/edit").with(user(staff)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post("/app/onboarding/edit").with(user(client)).with(csrf()).param("action","SUBMITTED").param("profile.address","My address").param("profile.fullName","Forged name").param("profile.lawfulBasis","CONSENT").param("profile.retentionReference","Forged policy").param("profile.selfService","false").param("clientId",id.toString()).param("state","VERIFY").param("noticeAcknowledged","true").param("declarationAccepted","true"))
            .andExpect(redirectedUrl("/app/onboarding"));
        verify(service).saveOwn(eq(client),argThat(f->f.getProfile().getAddress().equals("My address")&&f.getAction().equals("SUBMITTED")&&f.isNoticeAcknowledged()&&f.isDeclarationAccepted()));
    }
    @Test void ownOnboardingDoesNotRequireLoanPermissionsOrGrantOtherWorkspaceAccess()throws Exception {
        var noClaims=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.MEMBER).memberAccount(true).status(MemberStatus.ACTIVE).build(),Set.of(),false);
        mvc.perform(get("/app/onboarding").with(user(noClaims))).andExpect(status().isOk()).andExpect(view().name("onboarding/status"));
        mvc.perform(post("/app/onboarding/edit").with(user(noClaims)).with(csrf()).param("action","DRAFT")).andExpect(redirectedUrl("/app/onboarding"));
        mvc.perform(get("/app/loan-products").with(user(noClaims))).andExpect(status().isForbidden());verify(service).ownWorkspace(noClaims,0,null);verify(service).saveOwn(eq(noClaims),any());
    }
    @Test void clientValidationPreservesValuesAndAssociatesFieldErrors()throws Exception {
        when(service.saveOwn(any(),any())).thenThrow(new IllegalArgumentException("kyc.error.address"));
        var result=mvc.perform(post("/app/onboarding/edit").with(user(client)).with(csrf()).param("profile.identityNumber","TZ-1234")).andExpect(view().name("onboarding/self-form")).andExpect(model().attributeHasFieldErrors("selfForm","profile.address")).andReturn();
        assertThat(((SelfForm)result.getModelAndView().getModel().get("selfForm")).getProfile().getIdentityNumber()).isEqualTo("TZ-1234");
    }
    @Test void clientUploadKeepsRevisionAndOwnDownloadsArePrivate()throws Exception {
        UUID revision=UUID.randomUUID(),document=UUID.randomUUID();
        mvc.perform(multipart("/app/onboarding/documents/new").file("file",new byte[]{1}).param("revisionId",revision.toString()).param("category","ADDRESS").param("clientId",id.toString()).with(user(client)).with(csrf())).andExpect(redirectedUrl("/app/onboarding"));
        verify(service).attachOwn(eq(client),argThat(f->f.getRevisionId().equals(revision)&&f.getCategory().equals("ADDRESS")),any());
        when(service.downloadOwn(client,document)).thenReturn(new Download(new byte[]{1},"image/png","evidence.png"));
        mvc.perform(get("/app/onboarding/documents/"+document).with(user(client))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("Content-Security-Policy","sandbox"));
        when(service.downloadOwn(client,document)).thenThrow(new org.springframework.security.access.AccessDeniedException("Unavailable"));
        mvc.perform(get("/app/onboarding/documents/"+document).with(user(client))).andExpect(status().isForbidden());
    }
    @Test void changedNoticePreservesProfileAndRequiresFreshAcknowledgements()throws Exception {
        UUID next=UUID.randomUUID();when(service.ownNotice(client)).thenReturn(Optional.of(new Notice(next,2,true,"Notice 2","English","Kiswahili","CONSENT","Internal retention reference",java.time.LocalDate.of(2028,1,1))));
        when(service.saveOwn(any(),any())).thenThrow(new IllegalArgumentException("kyc.error.noticeChanged"));
        var result=mvc.perform(post("/app/onboarding/edit").with(user(client)).with(csrf()).param("noticeId",UUID.randomUUID().toString()).param("noticeAcknowledged","true").param("declarationAccepted","true").param("profile.address","Keep my address")).andExpect(view().name("onboarding/self-form")).andReturn();
        var form=(SelfForm)result.getModelAndView().getModel().get("selfForm");assertThat(form.getProfile().getAddress()).isEqualTo("Keep my address");assertThat(form.getNoticeId()).isEqualTo(next);assertThat(form.isNoticeAcknowledged()).isFalse();assertThat(form.isDeclarationAccepted()).isFalse();
    }
    @Test void noticeConfigurationRequiresStaffAndCsrf()throws Exception {
        mvc.perform(post(root+"/notice").with(user(staff))).andExpect(status().isForbidden());
        mvc.perform(post(root+"/notice").with(user(client)).with(csrf())).andExpect(status().isForbidden());
        mvc.perform(post(root+"/notice").with(user(staff)).with(csrf()).param("version","Approved 1").param("english","Approved notice")).andExpect(redirectedUrl(root+"/notice"));
        verify(service).saveNotice(eq(staff),argThat(f->f.getVersion().equals("Approved 1")));
    }
    @Test void onboardingClaimsAreExplicitAndHaveAStaffLanding() {
        var claims=Arrays.stream(UserClaim.values()).filter(c->c.getFeature()==AccessFeature.CLIENT_ONBOARDING).toList();assertThat(claims).hasSize(5);
        for(var role:Position.values())assertThat(UserClaim.defaultClaims(List.of(role),true)).doesNotContainAnyElementsOf(claims);
        assertThat(com.sacco.mvp.security.WorkspaceLanding.staffDashboard(new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I").stationId("B").position(Position.ACCOUNTANT).status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).build(),Set.of(UserClaim.CLIENT_ONBOARDING_VIEW),true))).isEqualTo(root);
    }
    @Test void allOnboardingLabelsAndErrorKeysHaveBothLanguages()throws Exception {
        var keys=new HashSet<String>();try(var files=Files.list(Path.of("src/main/webapp/WEB-INF/jsp/onboarding"))) {for(var file:files.toList()){var matcher=java.util.regex.Pattern.compile("code=[\"']([^\"'$]+)[\"']").matcher(Files.readString(file));while(matcher.find())keys.add(matcher.group(1));}}
        var en=ResourceBundle.getBundle("messages",Locale.ENGLISH);var sw=ResourceBundle.getBundle("messages",Locale.of("sw"));for(var key:en.keySet())if(key.startsWith("kyc.")||key.equals("credit.field.kycRequired"))keys.add(key);
        for(var key:keys){assertThat(en.containsKey(key)).as("English %s",key).isTrue();assertThat(sw.containsKey(key)).as("Kiswahili %s",key).isTrue();}
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean OnboardingService service(){return mock(OnboardingService.class);}
        @Bean OnboardingController controller(OnboardingService service){return new OnboardingController(service);}
        @Bean ClientOnboardingController clientController(OnboardingService service){return new ClientOnboardingController(service);}
    }
}
