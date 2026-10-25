package com.sacco.mvp.config;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.controller.GroupAuthorityController;
import com.sacco.mvp.grouplending.dto.GroupAuthorityDtos.*;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.Group;
import com.sacco.mvp.grouplending.service.GroupAuthorityService;
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
import java.nio.file.*;
import java.util.*;
import java.util.regex.Pattern;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,GroupAuthorityMvcTest.Config.class})
class GroupAuthorityMvcTest {
    @Autowired WebApplicationContext context;@Autowired GroupAuthorityService service;
    MockMvc mvc;AppUserPrincipal actor;final UUID group=UUID.randomUUID(),authority=UUID.randomUUID(),client=UUID.randomUUID();
    String path(){return "/finance/groups/"+group+"/authority";}
    @BeforeEach void setup(){reset(service);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),true);}
    void editor(){when(service.editor(eq(actor),eq(group),any(AuthorityForm.class))).thenAnswer(call->new Editor(new Group(group,"GR-1","Synthetic organization","ORGANIZATION","Evidence","ACTIVE",0,0),call.getArgument(2),Map.of()));}
    @Test void recordingRequiresCsrfAndBindsOnlyAuthorityInputs()throws Exception{
        mvc.perform(post(path()).with(user(actor))).andExpect(status().isForbidden());verifyNoInteractions(service);
        when(service.record(eq(actor),eq(group),any())).thenReturn(authority);
        mvc.perform(post(path()).with(user(actor)).with(csrf()).param("requestKey",UUID.randomUUID().toString()).param("verified","true").param("maxPrincipal","10000.00").param("signers[0].clientId",client.toString()).param("signers[0].maySign","true").param("saccoId","FOREIGN").param("stationId","OTHER").param("recordedBy",client.toString()).param("revision","99"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path()+"/"+authority));
        verify(service).record(eq(actor),eq(group),argThat(f->f.isVerified() && f.getMaxPrincipal().toPlainString().equals("10000.00") && f.getSigners().size()==1 && f.getSigners().getFirst().getClientId().equals(client) && f.getSigners().getFirst().isMaySign() && !f.getSigners().getFirst().isMayApply()));
    }
    @Test void invalidAmountsRetainBoundValuesAndDoNotCreateAuthority()throws Exception{
        editor();var result=mvc.perform(post(path()).with(user(actor)).with(csrf()).param("maxPrincipal","not-money").param("capacityEvidence","Retained source reference").param("signers[0].clientId",client.toString()))
            .andExpect(status().isOk()).andExpect(view().name("groups/authority-form")).andExpect(model().attributeHasFieldErrors("authorityForm","maxPrincipal")).andReturn();
        var binding=(org.springframework.validation.BindingResult)result.getModelAndView().getModel().get(org.springframework.validation.BindingResult.MODEL_KEY_PREFIX+"authorityForm");
        assertThat(context.getMessage(binding.getFieldError("maxPrincipal"),Locale.ENGLISH)).isEqualTo("Enter a valid amount.");
        assertThat(context.getMessage(binding.getFieldError("maxPrincipal"),Locale.of("sw"))).isEqualTo("Weka kiasi sahihi.");
        assertThat(binding.getFieldError("maxPrincipal").getRejectedValue()).isEqualTo("not-money");
        verify(service,never()).record(any(),any(),any());verify(service).editor(eq(actor),eq(group),argThat(f->f.getCapacityEvidence().equals("Retained source reference") && f.getSigners().getFirst().getClientId().equals(client)));
    }
    @Test void rowCommandsKeepEvidenceWithoutRecordingPartialAuthority()throws Exception{
        editor();mvc.perform(post(path()).with(user(actor)).with(csrf()).param("action","addSigner").param("resolutionEvidence","Retained resolution").param("signers[0].clientId",client.toString()).param("signers[0].mayApply","true"))
            .andExpect(status().isOk()).andExpect(view().name("groups/authority-form"));
        verify(service).editor(eq(actor),eq(group),argThat(f->f.getSigners().size()==2 && f.getSigners().getFirst().isMayApply() && f.getResolutionEvidence().equals("Retained resolution")));verify(service,never()).record(any(),any(),any());
    }
    @Test void revocationRequiresCsrfAndUsesTheAuthenticatedActorAndScopedRecord()throws Exception{
        mvc.perform(post(path()+"/"+authority+"/revoke").with(user(actor)).param("reason","Synthetic revocation")).andExpect(status().isForbidden());verifyNoInteractions(service);
        mvc.perform(post(path()+"/"+authority+"/revoke").with(user(actor)).with(csrf()).param("reason","Synthetic revocation").param("actorId",client.toString()).param("groupId",UUID.randomUUID().toString()))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path()+"/"+authority));verify(service).revoke(actor,group,authority,"Synthetic revocation");
    }
    @Test void revokedOperationPermissionCannotBeBypassedThroughTheController()throws Exception{
        doThrow(new AccessDeniedException("Revoked")).when(service).authorize(actor,"CREATE");mvc.perform(post(path()).with(user(actor)).with(csrf()).param("verified","true")).andExpect(status().isForbidden());verify(service,never()).record(any(),any(),any());
    }
    @Test void clientSessionsCannotEnterStaffAuthorityRoutes()throws Exception{
        var principal=new AppUserPrincipal(Member.builder().id(client).saccoId("I1").stationId("B1").position(Position.MEMBER).memberAccount(true).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),false);
        mvc.perform(get(path()).with(user(principal))).andExpect(status().isForbidden());mvc.perform(post(path()).with(user(principal)).with(csrf())).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void authorityLabelsAndDynamicFieldsExistInBothLanguages()throws Exception{
        var keys=new HashSet<String>();for(var file:List.of("authority-history.jsp","authority-form.jsp","authority-details.jsp","authority-revoke.jsp")){var source=Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/groups",file));var matcher=Pattern.compile("<spring:message code=\"([^\"$]+)\"").matcher(source);while(matcher.find())keys.add(matcher.group(1));}
        for(var locale:List.of(Locale.ENGLISH,Locale.of("sw"))){var messages=ResourceBundle.getBundle("messages",locale);for(var key:keys)assertThat(messages.containsKey(key)).as("%s %s",locale,key).isTrue();for(var field:List.of("groupName","groupCode","maxPrincipal","validFrom","validThrough","recordedByName","recordedAt","capacityEvidence","resolutionEvidence","signatureEvidence","payoutProvider","payoutHolder","payoutIdentifier","payoutEvidence","revocationReason","revokedByName","revokedAt","mayApply","maySign","mayReceive"))assertThat(messages.containsKey("authority."+field)).as("%s %s",locale,field).isTrue();}
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean GroupAuthorityService authorityService(){return mock(GroupAuthorityService.class);}
        @Bean GroupAuthorityController authorityController(GroupAuthorityService service){return new GroupAuthorityController(service);}
        @Bean ResourceBundleMessageSource messageSource(){var m=new ResourceBundleMessageSource();m.setBasename("messages");m.setDefaultEncoding("UTF-8");return m;}
    }
}
