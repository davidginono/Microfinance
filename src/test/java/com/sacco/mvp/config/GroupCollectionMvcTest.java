package com.sacco.mvp.config;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.controller.GroupCollectionController;
import com.sacco.mvp.grouplending.dto.GroupCollectionDtos;
import com.sacco.mvp.grouplending.service.GroupCollectionService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.math.BigDecimal;
import java.time.LocalDate;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,GroupCollectionMvcTest.Config.class})
class GroupCollectionMvcTest {
    @Autowired WebApplicationContext context;@Autowired GroupCollectionService collections;
    MockMvc mvc;AppUserPrincipal actor;final UUID cycle=UUID.randomUUID(),payer=UUID.randomUUID(),loan=UUID.randomUUID(),receipt=UUID.randomUUID();
    String path(){return "/finance/groups/cycles/"+cycle+"/receipts";}
    @BeforeEach void setup(){reset(collections);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
        actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),true);
        when(collections.cycle(actor,cycle)).thenReturn(new GroupCollectionDtos.Cycle(cycle,"GRP-1","Synthetic group","INDIVIDUAL","SUBMITTED"));when(collections.selected(eq(actor),eq(cycle),any())).thenReturn(Map.of(payer,"C1 - Payer",loan,"LN1 - Borrower"));}
    @Test void postingRequiresCsrfAndStaffSession()throws Exception{
        mvc.perform(post(path()).with(user(actor)).param("action","post")).andExpect(status().isForbidden());
        var member=new AppUserPrincipal(Member.builder().id(payer).saccoId("I1").stationId("B1").memberAccount(true).position(Position.MEMBER).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),false);
        mvc.perform(get(path()+"/new").with(user(member))).andExpect(status().isForbidden());verifyNoInteractions(collections);
    }
    @Test void previewBindsCommandsAndNeverAcceptsFinancialAllocationsOrScope()throws Exception{
        when(collections.preview(eq(actor),eq(cycle),any())).thenReturn(new GroupCollectionDtos.Preview("server-hash","RV-1",List.of(),List.of(),new BigDecimal("120.00")));
        mvc.perform(post(path()).with(user(actor)).with(csrf()).param("action","preview").param("remitter","Synthetic treasurer").param("rows[0].loanId",loan.toString()).param("rows[0].payerId",payer.toString()).param("rows[0].amount","120.00").param("rows[0].principal","999").param("stationId","FOREIGN"))
            .andExpect(status().isOk()).andExpect(view().name("groups/collection-form")).andExpect(model().attributeExists("preview"));
        verify(collections).preview(eq(actor),eq(cycle),argThat(f->f.getRows().getFirst().getLoanId().equals(loan) && f.getRows().getFirst().getAmount().compareTo(new BigDecimal("120.00"))==0 && f.getRemitter().equals("Synthetic treasurer")));
        verify(collections,never()).post(any(),any(),any());
    }
    @Test void invalidAmountsRetainPayerAndBorrowerWithoutPosting()throws Exception{
        mvc.perform(post(path()).with(user(actor)).with(csrf()).param("action","post").param("rows[0].amount","invalid").param("rows[0].payerId",payer.toString()).param("rows[0].loanId",loan.toString()))
            .andExpect(status().isOk()).andExpect(view().name("groups/collection-form")).andExpect(model().attributeHasErrors("collectionForm")).andExpect(model().attributeExists("labels"));verify(collections,never()).post(any(),any(),any());
    }
    @Test void stalePreviewShowsRecoveryWithoutKeepingThePostPreview()throws Exception{
        when(collections.post(eq(actor),eq(cycle),any())).thenThrow(new IllegalArgumentException("group.error.previewChanged"));
        mvc.perform(post(path()).with(user(actor)).with(csrf()).param("action","post").param("previewHash","old"))
            .andExpect(status().isOk()).andExpect(model().attributeHasErrors("collectionForm")).andExpect(model().attributeDoesNotExist("preview"));
    }
    @Test void rowCommandsRetainValuesWithoutPreviewOrPosting()throws Exception{
        mvc.perform(post(path()).with(user(actor)).with(csrf()).param("action","addRow").param("rows[0].payerId",payer.toString()).param("rows[0].amount","120.00").param("previewHash","old"))
            .andExpect(status().isOk()).andExpect(view().name("groups/collection-form"));
        verify(collections).selected(eq(actor),eq(cycle),argThat(f->f.getRows().size()==2 && f.getRows().getFirst().getPayerId().equals(payer) && f.getPreviewHash().isEmpty()));verify(collections,never()).post(any(),any(),any());verify(collections,never()).preview(any(),any(),any());
    }
    @Test void oversizedCollectionsReturnABadRequest()throws Exception{
        mvc.perform(post(path()).with(user(actor)).with(csrf()).param("rows[50].loanId",loan.toString())).andExpect(status().isBadRequest()).andExpect(view().name("groups/error"));verify(collections,never()).post(any(),any(),any());
    }
    @Test void sharedCreateAndNewRowsUseTheOneScopedParentWithoutAcceptingBeneficiaryComponents()throws Exception{
        when(collections.cycle(actor,cycle)).thenReturn(new GroupCollectionDtos.Cycle(cycle,"GRP-1","Synthetic shared borrowers","SHARED","SUBMITTED"));
        when(collections.choices(actor,cycle,"loans","",0)).thenReturn(List.of(new com.sacco.mvp.grouplending.dto.GroupLendingDtos.Choice(loan,"SHARED-1")));
        mvc.perform(get(path()+"/new").with(user(actor))).andExpect(status().isOk()).andExpect(model().attributeExists("sharedLoan"));
        verify(collections).selected(eq(actor),eq(cycle),argThat(f->loan.equals(f.getRows().getFirst().getLoanId())));
        mvc.perform(post(path()).with(user(actor)).with(csrf()).param("action","addRow").param("rows[0].loanId",loan.toString()).param("rows[0].payerId",payer.toString()).param("rows[0].amount","30.00"))
            .andExpect(status().isOk()).andExpect(model().attribute("collectionForm",org.hamcrest.Matchers.hasProperty("rows",org.hamcrest.Matchers.hasSize(2))));
        verify(collections).selected(eq(actor),eq(cycle),argThat(f->f.getRows().size()==2&&f.getRows().stream().allMatch(r->loan.equals(r.getLoanId()))));verify(collections,never()).post(any(),any(),any());
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean GroupCollectionService collections(){return mock(GroupCollectionService.class);}
        @Bean ApplicationClock clock(){var c=mock(ApplicationClock.class);when(c.today()).thenReturn(LocalDate.of(2026,10,25));return c;}
        @Bean ResourceBundleMessageSource messageSource(){var m=new ResourceBundleMessageSource();m.setBasename("messages");m.setDefaultEncoding("UTF-8");return m;}
        @Bean GroupCollectionController controller(GroupCollectionService c,ApplicationClock clock){return new GroupCollectionController(c,clock);}
    }
}
