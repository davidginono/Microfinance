package com.sacco.mvp.config;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.controller.GroupOrganizationAccountController;
import com.sacco.mvp.grouplending.dto.GroupCycleStatementDtos.Statement;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,GroupOrganizationAccountMvcTest.Config.class})
class GroupOrganizationAccountMvcTest {
    @Autowired WebApplicationContext context;@Autowired GroupOrganizationAccountService statements;@Autowired GroupCycleStatementPdf pdf;
    MockMvc mvc;AppUserPrincipal client;UUID cycle=UUID.randomUUID();
    @BeforeEach void setup(){reset(statements,pdf);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();client=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").status(MemberStatus.ACTIVE).memberAccount(true).position(Position.MEMBER).build(),EnumSet.allOf(UserClaim.class),false);}
    @Test void personalRouteUsesAuthenticatedOwnerAndExplicitDateParameters()throws Exception{
        mvc.perform(get("/app/group-loans/"+cycle+"/organization-account").with(user(client)).param("clientId",UUID.randomUUID().toString()).param("from","2026-10-01").param("through","2026-10-08").param("page","1").param("coveragePage","2")).andExpect(status().isOk()).andExpect(view().name("groups/cycle-statement"));verify(statements).statement(client,cycle,LocalDate.of(2026,10,1),LocalDate.of(2026,10,8),1,2,false);
        mvc.perform(get("/app/group-loans/"+cycle+"/organization-account")).andExpect(status().is3xxRedirection());
    }
    @Test void pdfAndPrintUseAuthorizedCompleteExportAndPrivateHeaders()throws Exception{
        var data=mock(Statement.class);when(statements.statement(client,cycle,null,null,0,true)).thenReturn(data);when(pdf.render(data,Locale.ENGLISH)).thenReturn("%PDF-test".getBytes());
        for(var format:List.of("pdf","print"))mvc.perform(get("/app/group-loans/"+cycle+"/organization-account/"+format).with(user(client)).locale(Locale.ENGLISH)).andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("Content-Disposition",(format.equals("pdf")?"attachment":"inline")+"; filename=organization-account-statement.pdf"));
    }
    @Test void malformedDateAndExcessiveRangeFailRecoverably()throws Exception{
        mvc.perform(get("/app/group-loans/"+cycle+"/organization-account").with(user(client)).param("from","not-a-date")).andExpect(status().isBadRequest());verifyNoInteractions(statements);
        when(statements.statement(client,cycle,null,null,0,0,false)).thenThrow(new IllegalArgumentException("group.error.statementRange"));mvc.perform(get("/app/group-loans/"+cycle+"/organization-account").with(user(client))).andExpect(status().isBadRequest()).andExpect(model().attribute("groupError","group.error.statementRange"));
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean GroupOrganizationAccountService statements(){return mock(GroupOrganizationAccountService.class);}
        @Bean GroupCycleStatementPdf pdf(){return mock(GroupCycleStatementPdf.class);}
        @Bean GroupOrganizationAccountController controller(GroupOrganizationAccountService s,GroupCycleStatementPdf p){return new GroupOrganizationAccountController(s,p);}
    }
}
