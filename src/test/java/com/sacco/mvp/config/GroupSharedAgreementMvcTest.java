package com.sacco.mvp.config;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.controller.GroupSharedAgreementController;
import com.sacco.mvp.grouplending.dto.GroupOrganizationDocumentDtos.DocumentForm;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,GroupSharedAgreementMvcTest.Config.class})
class GroupSharedAgreementMvcTest {
    @Autowired WebApplicationContext context;@Autowired GroupSharedAgreementService agreements;@Autowired GroupSharedAgreementDeliveryService delivery;
    MockMvc mvc;AppUserPrincipal staff,client;final UUID cycle=UUID.randomUUID();
    @BeforeEach void setup(){reset(agreements,delivery);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        staff=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).position(Position.ACCOUNTANT).build(),EnumSet.allOf(UserClaim.class),true);
        client=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").status(MemberStatus.ACTIVE).memberAccount(true).position(Position.MEMBER).build(),EnumSet.allOf(UserClaim.class),false);
    }
    @Test void fullDocumentUploadRequiresCsrfAndCannotBindActorOrScope()throws Exception{
        String path="/finance/groups/cycles/"+cycle+"/agreement";var file=new MockMultipartFile("agreementPdf","terms.pdf","application/pdf","synthetic test".getBytes());
        mvc.perform(multipart(path).file(file).with(user(staff))).andExpect(status().isForbidden());verifyNoInteractions(agreements);
        mvc.perform(multipart(path).file(file).with(user(staff)).with(csrf()).param("documentReference","Approved shared terms").param("documentChecked","true").param("saccoId","FOREIGN").param("clientId",UUID.randomUUID().toString()).param("expectedDocumentId",UUID.randomUUID().toString()))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path));
        verify(agreements).record(eq(staff),eq(cycle),argThat(f->f.isDocumentChecked()&&f.getExpectedDocumentId()==null&&f.getDocumentReference().equals("Approved shared terms")),any());
    }
    @Test void payoutConfirmationUsesAuthenticatedPersonAndExactPacket()throws Exception{
        String path="/app/group-loans/"+cycle+"/payout/confirm";mvc.perform(post(path).with(user(client)).param("packetHash","packet")).andExpect(status().isForbidden());verifyNoInteractions(agreements);
        mvc.perform(post(path).with(user(client)).with(csrf()).param("packetHash","packet").param("accepted","true").param("otpCode","123456").param("clientId",UUID.randomUUID().toString()).param("netAmount","9000000").param("destination.accountIdentifier","FORGED"))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl("/app/group-loans/"+cycle+"/payout"));verify(agreements).confirm(client,cycle,"packet",true,"123456");
    }
    @Test void incorrectCodeReturnsRecoverableFeedback()throws Exception{
        when(agreements.confirm(client,cycle,"packet",true,"000000")).thenReturn("group.error.otpInvalid");
        mvc.perform(post("/app/group-loans/"+cycle+"/payout/confirm").with(user(client)).with(csrf()).param("packetHash","packet").param("accepted","true").param("otpCode","000000"))
            .andExpect(status().is3xxRedirection()).andExpect(flash().attribute("consentError","group.error.otpInvalid"));
    }
    @Test void personalPayoutCodesHaveTheirOwnCsrfProtectedCommand()throws Exception{
        String path="/app/group-loans/"+cycle+"/payout/code";mvc.perform(post(path).with(user(client)).param("packetHash","packet")).andExpect(status().isForbidden());verifyNoInteractions(delivery);
        mvc.perform(post(path).with(user(client)).with(csrf()).locale(Locale.ENGLISH).param("packetHash","packet")).andExpect(status().is3xxRedirection()).andExpect(flash().attribute("consentCodeSent",true));verify(delivery).send(client,cycle,"packet",Locale.ENGLISH);
    }
    @Test void scopedFullAgreementDownloadHasPdfAndNoStoreHeaders()throws Exception{
        when(agreements.exportClient(client,cycle,Locale.ENGLISH)).thenReturn("%PDF-test".getBytes());
        mvc.perform(get("/app/group-loans/"+cycle+"/payout/pdf").with(user(client)).locale(Locale.ENGLISH)).andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andExpect(header().string("Cache-Control","no-store"));verify(agreements).exportClient(client,cycle,Locale.ENGLISH);
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean GroupSharedAgreementService agreements(){return mock(GroupSharedAgreementService.class);}
        @Bean GroupSharedAgreementDeliveryService delivery(){return mock(GroupSharedAgreementDeliveryService.class);}
        @Bean GroupCycleService cycles(){return mock(GroupCycleService.class);}
        @Bean GroupSharedAgreementController controller(GroupSharedAgreementService a,GroupSharedAgreementDeliveryService d,GroupCycleService c){return new GroupSharedAgreementController(a,d,c);}
    }
}
