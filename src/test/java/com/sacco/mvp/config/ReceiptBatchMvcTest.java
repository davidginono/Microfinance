package com.sacco.mvp.config;

import com.sacco.mvp.accounting.batch.controller.ReceiptBatchController;
import com.sacco.mvp.accounting.batch.controller.ReceiptBatchSpreadsheetController;
import com.sacco.mvp.accounting.batch.dto.ReceiptBatchDtos.*;
import com.sacco.mvp.accounting.batch.service.ReceiptBatchService;
import com.sacco.mvp.accounting.batch.service.ReceiptBatchWorkbook;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.Choice;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.ReceiptEntryLoan;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.repayment.service.RepaymentPolicyService;
import com.sacco.mvp.accounting.service.LoanReceiptService;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.ReportExportLimiter;
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
import java.time.*;
import java.util.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,ReceiptBatchMvcTest.Config.class})
class ReceiptBatchMvcTest {
    @Autowired WebApplicationContext context;
    @Autowired ReceiptBatchService batches;
    MockMvc mvc;AppUserPrincipal actor;UUID batch=UUID.randomUUID(),item=UUID.randomUUID();
    @BeforeEach void setup(){reset(batches);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();var member=Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("TEST").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build();actor=new AppUserPrincipal(member,EnumSet.allOf(UserClaim.class));}
    @Test void mutationsRequireCsrfAndClientSessionsCannotAccessWorkspace()throws Exception{
        mvc.perform(put("/finance/batches/receipts/"+batch+"/items/"+item).with(user(actor)).contentType("application/json").content("{\"expectedVersion\":0,\"form\":{}}" )).andExpect(status().isForbidden());
        var client=com.sacco.mvp.config.MvcSecurityTestConfiguration.formerClient(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").status(MemberStatus.ACTIVE).build(),Set.of());
        mvc.perform(get("/finance/batches/receipts").with(user(client))).andExpect(status().isForbidden());verifyNoInteractions(batches);
    }
    @Test void registerCreatesADraftDirectlyWithScopedClaimsAndCsrf() throws Exception {
        String path = "/finance/batches/receipts";
        var register = mvc.perform(get(path).with(user(actor)))
            .andExpect(status().isOk()).andExpect(view().name("accounting/batches/register")).andReturn();
        UUID requestKey = (UUID) register.getModelAndView().getModel().get("createRequestKey");
        when(batches.create(actor, requestKey)).thenReturn(batch);
        var staff = Member.builder().id(actor.getMemberId()).saccoId(actor.getSaccoId()).stationId(actor.getStationId())
            .memberNo(actor.getMemberNo()).position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE)
            .status(MemberStatus.ACTIVE).build();
        var readOnly = new AppUserPrincipal(staff, EnumSet.of(UserClaim.ACCOUNTING_BUSINESS_VIEW, UserClaim.LOAN_RECORDING_VIEW));
        mvc.perform(post(path).with(user(actor)).param("requestKey", requestKey.toString()))
            .andExpect(status().isForbidden());
        mvc.perform(post(path).with(user(readOnly)).with(csrf()).param("requestKey", requestKey.toString()))
            .andExpect(status().isForbidden());
        mvc.perform(post(path).with(user(actor)).with(csrf()).param("requestKey", requestKey.toString())
                .param("title", "Ignored manual title"))
            .andExpect(redirectedUrl(path + "/" + batch)).andExpect(flash().attribute("successMessageCode", "batch.createSuccess"));
        mvc.perform(get(path + "/new").with(user(actor))).andExpect(status().is4xxClientError());
        mvc.perform(post(path + "/new").with(user(actor)).with(csrf()).param("title", "Retired form"))
            .andExpect(status().is4xxClientError());
        verify(batches).list(actor, "", 0);
        verify(batches).create(actor, requestKey);
        verifyNoMoreInteractions(batches);
    }
    @Test void jsonMoneyKeepsExactDecimalInputAndScopeComesFromPrincipal()throws Exception{
        mvc.perform(put("/finance/batches/receipts/"+batch+"/items/"+item).with(user(actor)).with(csrf()).contentType("application/json").content("{\"expectedVersion\":2,\"form\":{\"rows\":[{\"amount\":\"9999999999999999.99\"}]}}" )).andExpect(status().isOk());
        verify(batches).save(eq(actor),eq(batch),eq(item),argThat(f->f.expectedVersion()==2 && f.form().getRows().getFirst().getAmount().toPlainString().equals("9999999999999999.99")));
    }
    @Test void linkingConfiguredAccountsRequiresCsrfAndUsesTheSavedItemVersion()throws Exception{
        String path="/finance/batches/receipts/"+batch+"/items/"+item+"/link-accounts";
        mvc.perform(post(path).with(user(actor)).contentType("application/json").content("{\"expectedVersion\":3}")).andExpect(status().isForbidden());
        verifyNoInteractions(batches);
        mvc.perform(post(path).with(user(actor)).with(csrf()).contentType("application/json").content("{\"expectedVersion\":3,\"stationId\":\"FOREIGN\"}")).andExpect(status().isOk());
        verify(batches).linkAccounts(actor,batch,item,new LinkAccounts(3));
    }
    @Test void postingUsesReviewedVersionAndDoesNotInvokeAnApprovalWorkflow()throws Exception{
        var b=new Batch(batch,"I1","B1",actor.getMemberId(),"Collections",7,"POSTING",false,null,OffsetDateTime.now());when(batches.get(actor,batch)).thenReturn(new Summary(b,2,0,2,0,new BigDecimal("3.03"),BigDecimal.ZERO));
        mvc.perform(post("/finance/batches/receipts/"+batch+"/post?version=6").with(user(actor)).with(csrf())).andExpect(status().isOk()).andExpect(jsonPath("$.remainingTotal").value("3.03")).andExpect(header().string("Cache-Control","no-store"));verify(batches).start(actor,batch,6,true);
    }
    @Test void completedPostingReturnsToTheBatchRegisterWithASuccessNotice()throws Exception{
        String path="/finance/batches/receipts/"+batch;
        var completed=new Batch(batch,"I1","B1",actor.getMemberId(),"Collections",1,"COMPLETED",false,null,null);
        var partial=new Batch(batch,"I1","B1",actor.getMemberId(),"Collections",1,"PARTIAL",false,null,null);
        when(batches.get(actor,batch)).thenReturn(
            new Summary(completed,2,2,0,0,BigDecimal.TEN,BigDecimal.TEN),
            new Summary(partial,2,1,0,1,BigDecimal.TEN,BigDecimal.ONE));
        mvc.perform(get(path+"/posted").with(user(actor))).andExpect(redirectedUrl("/finance/batches/receipts"))
            .andExpect(flash().attribute("successMessageCode","batch.postSuccess"));
        mvc.perform(get(path+"/posted").with(user(actor))).andExpect(redirectedUrl(path))
            .andExpect(flash().attributeCount(0));
    }
    @Test void validationErrorsAreLocalisedAndDoNotExposeExceptions()throws Exception{
        doThrow(new IllegalArgumentException("batch.error.changed")).when(batches).start(actor,batch,0,true);
        mvc.perform(post("/finance/batches/receipts/"+batch+"/post?version=0").with(user(actor)).with(csrf()).locale(Locale.forLanguageTag("sw"))).andExpect(status().isBadRequest()).andExpect(jsonPath("$.code").value("batch.error.changed")).andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("imebadilika")));
    }
    @Test void repaymentSettingsCannotBeOpenedOrChanged()throws Exception{
        mvc.perform(get("/finance/batches/receipts/settings").with(user(actor))).andExpect(status().is4xxClientError());
        mvc.perform(get("/finance/batches/receipts/settings/accounts").with(user(actor))).andExpect(status().isNotFound());
        mvc.perform(post("/finance/batches/receipts/settings").with(user(actor)).with(csrf())).andExpect(status().is4xxClientError());
        verifyNoInteractions(context.getBean(RepaymentPolicyService.class));
    }
    @Test void oversizedChunkedAndDeclaredBodiesAreRejectedBeforeBinding()throws Exception{
        var filter=new com.sacco.mvp.accounting.batch.controller.ReceiptBatchBodyLimit();
        for(boolean chunked:List.of(false,true)){
            var request=new org.springframework.mock.web.MockHttpServletRequest("PUT","/finance/batches/receipts/"+batch+"/items/"+item){@Override public long getContentLengthLong(){return chunked?-1:super.getContentLengthLong();}};
            request.setContentType("application/json");request.setContent(new byte[128*1024+1]);var response=new org.springframework.mock.web.MockHttpServletResponse();var chain=new org.springframework.mock.web.MockFilterChain();filter.doFilter(request,response,chain);
            org.assertj.core.api.Assertions.assertThat(response.getStatus()).isEqualTo(413);org.assertj.core.api.Assertions.assertThat(chain.getRequest()).isNull();
        }
    }
    @Test void spreadsheetDownloadAndUploadUseTheSameBatchAndRequireCsrf() throws Exception {
        var path = "/finance/batches/receipts/" + batch + "/spreadsheet";
        var date = LocalDate.of(2026, 11, 9);
        var money = UUID.randomUUID();
        var client = UUID.randomUUID();
        when(batches.spreadsheetBatch(actor, batch)).thenReturn(new Batch(batch, "I1", "B1",
            actor.getMemberId(), "Collections", 1, "DRAFT", false, null, OffsetDateTime.now()));
        when(batches.templateLoans(actor, batch, "")).thenReturn(List.of(new ReceiptEntryLoan(UUID.randomUUID(),
            client, "0001", "Client A", "001", "Business", date.minusMonths(1), date, BigDecimal.TEN)));
        var downloaded = mvc.perform(get(path + "/template.xlsx").with(user(actor)))
            .andExpect(status().isOk()).andExpect(header().string("Cache-Control", "no-store"))
            .andExpect(header().string("Content-Disposition", "attachment; filename=\"repayment-entry.xlsx\""))
            .andReturn().getResponse().getContentAsByteArray();
        byte[] completed;
        try (var file = new org.apache.poi.xssf.usermodel.XSSFWorkbook(new java.io.ByteArrayInputStream(downloaded));
             var output = new java.io.ByteArrayOutputStream()) {
            var row = file.getSheetAt(0).getRow(2);
            row.getCell(4).setCellValue("11.03");
            row.getCell(5).setCellValue(date.toString());
            row.getCell(6).setCellValue("00009");
            file.write(output);
            completed = output.toByteArray();
        }
        var upload = new org.springframework.mock.web.MockMultipartFile("file", "repayment-entry.xlsx",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet", completed);
        mvc.perform(multipart(path).file(upload).param("moneyAccountId", money.toString()).with(user(actor)))
            .andExpect(status().isForbidden());
        var rows = List.of(new ImportRow(3, "0001", new BigDecimal("11.03"), date, "00009", client));
        verify(batches, never()).importDrafts(any(), any(), any(), any());
        when(context.getBean(LoanRecordingService.class).receiptMoneyAccount(actor, money))
            .thenReturn(new Choice(money, "Bank", "MONEY"));
        when(batches.importDrafts(actor, batch, money, rows)).thenReturn(1);
        mvc.perform(multipart(path).file(upload).param("moneyAccountId", money.toString()).with(user(actor)).with(csrf()))
            .andExpect(redirectedUrl("/finance/batches/receipts/" + batch)).andExpect(flash().attribute("importedReceiptCount", 1));
        verify(batches).importDrafts(actor, batch, money, rows);
    }
    @Configuration @EnableWebMvc static class Config {
        @Bean("access") @Primary com.sacco.mvp.service.AccessControlService access(){return new com.sacco.mvp.service.AccessControlService();}
        @Bean ReceiptBatchService batches(){return mock(ReceiptBatchService.class);}
        @Bean LoanReceiptService receipts(){return mock(LoanReceiptService.class);}
        @Bean LoanRecordingService loans(){return mock(LoanRecordingService.class);}
        @Bean RepaymentPolicyService policies(){return mock(RepaymentPolicyService.class);}
        @Bean ApplicationClock clock(){return mock(ApplicationClock.class);}
        @Bean ResourceBundleMessageSource messageSource(){var m=new ResourceBundleMessageSource();m.setBasename("messages");m.setDefaultEncoding("UTF-8");return m;}
        @Bean ReceiptBatchController controller(ReceiptBatchService batches,LoanReceiptService receipts,LoanRecordingService loans,RepaymentPolicyService policies,ApplicationClock clock,ResourceBundleMessageSource messages){return new ReceiptBatchController(batches,receipts,loans,policies,clock,messages);}
        @Bean ReceiptBatchSpreadsheetController spreadsheetController(ReceiptBatchService batches, LoanRecordingService loans,
                                                                    ResourceBundleMessageSource messages) {
            return new ReceiptBatchSpreadsheetController(batches, new ReceiptBatchWorkbook(messages), loans,
                new ReportExportLimiter(1), messages);
        }
    }
}
