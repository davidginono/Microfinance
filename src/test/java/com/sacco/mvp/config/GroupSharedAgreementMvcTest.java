package com.sacco.mvp.config;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.controller.GroupSharedAgreementController;
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
    @Autowired WebApplicationContext context;@Autowired GroupSharedAgreementService agreements;
    MockMvc mvc;AppUserPrincipal staff,client;final UUID cycle=UUID.randomUUID();
    @BeforeEach void setup(){reset(agreements);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        staff=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").status(MemberStatus.ACTIVE).staffAccessStatus(StaffAccessStatus.ACTIVE).position(Position.ACCOUNTANT).build(),EnumSet.allOf(UserClaim.class));
        client=com.sacco.mvp.config.MvcSecurityTestConfiguration.formerClient(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").status(MemberStatus.ACTIVE).memberAccount(true).position(Position.MEMBER).build(),EnumSet.allOf(UserClaim.class));
    }
    @Test void fullDocumentUploadRequiresCsrfAndCannotBindActorOrScope()throws Exception{
        String path="/finance/groups/cycles/"+cycle+"/agreement";var file=new MockMultipartFile("agreementPdf","terms.pdf","application/pdf","synthetic test".getBytes());
        mvc.perform(multipart(path).file(file).with(user(staff))).andExpect(status().isForbidden());verifyNoInteractions(agreements);
        mvc.perform(multipart(path).file(file).with(user(staff)).with(csrf()).param("documentReference","Approved shared terms").param("documentChecked","true").param("saccoId","FOREIGN").param("clientId",UUID.randomUUID().toString()).param("expectedDocumentId",UUID.randomUUID().toString()))
            .andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(path));
        verify(agreements).record(eq(staff),eq(cycle),argThat(f->f.isDocumentChecked()&&f.getExpectedDocumentId()==null&&f.getDocumentReference().equals("Approved shared terms")),any());
    }

    @Configuration @EnableWebMvc static class Config {
        @Bean GroupSharedAgreementService agreements(){return mock(GroupSharedAgreementService.class);}

        @Bean GroupSharedAgreementController controller(GroupSharedAgreementService agreements,GroupCycleService cycles){return new GroupSharedAgreementController(agreements,cycles);}
        @Bean GroupCycleService cycles(){return mock(GroupCycleService.class);}

    }
}
