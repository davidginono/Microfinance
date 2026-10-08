package com.sacco.mvp.config;
import com.sacco.mvp.accounting.controller.VoucherController;
import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.accounting.service.*;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringJUnitWebConfig({LoginCsrfAccessDeniedTest.TestConfig.class,VoucherMvcTest.Config.class})
class VoucherMvcTest {
 @Autowired WebApplicationContext context;@Autowired VoucherService service;@Autowired VoucherPdfService pdf;
 MockMvc mvc;AppUserPrincipal actor;UUID id=UUID.randomUUID();
 @BeforeEach void setup(){
  reset(service,pdf);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
  var m=Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("SYNTHETIC").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build();actor=new AppUserPrincipal(m,EnumSet.allOf(UserClaim.class),true);
  lenient().when(service.selectedAccounts(any(),any(),any())).thenReturn(Map.of());
 }
 @Test void multipleRowsBindAndTenantAndPostedTotalsAreNotAccepted()throws Exception{
  when(service.post(any(),eq(Type.RECEIPT),any())).thenReturn(voucher());
  mvc.perform(post("/finance/vouchers/receipts").with(user(actor)).with(csrf()).param("effectiveDate","2026-10-08").param("party","Payer").param("reference","REF")
   .param("rows[0].description","First").param("rows[0].amount","1.01").param("rows[1].description","Second").param("rows[1].amount","2.02").param("saccoId","FOREIGN").param("total","999"))
   .andExpect(redirectedUrl("/finance/vouchers/receipts/"+id));
  verify(service).post(eq(actor),eq(Type.RECEIPT),argThat(f->f.getRows().size()==2 && f.getRows().get(1).getAmount().compareTo(new BigDecimal("2.02"))==0));
 }
 @Test void previewDoesNotPostAndBindingErrorsKeepUserValues()throws Exception{
  when(service.preview(any(),any(),any())).thenReturn(new Preview(List.of(),BigDecimal.ZERO));
  mvc.perform(post("/finance/vouchers/journals").with(user(actor)).with(csrf()).param("action","preview").param("party","Context").param("rows[0].amount","2.02"))
   .andExpect(view().name("accounting/vouchers/form")).andExpect(model().attributeExists("preview"));verify(service,never()).post(any(),any(),any());
  var response=mvc.perform(post("/finance/vouchers/payments").with(user(actor)).with(csrf()).param("party","Supplier name").param("rows[0].amount","invalid"))
   .andExpect(model().attributeHasFieldErrors("voucherForm","rows[0].amount")).andReturn();
  assertThat(((Form)response.getModelAndView().getModel().get("voucherForm")).getParty()).isEqualTo("Supplier name");
 }
 @Test void csrfClientsAndOversizedRowsCannotPost()throws Exception{
  mvc.perform(post("/finance/vouchers/receipts").with(user(actor))).andExpect(status().isForbidden());
  mvc.perform(get("/documents/reports/loans.xlsx").with(user(actor))).andExpect(status().isForbidden());
  mvc.perform(get("/documents/reports/loans.csv").with(user(actor))).andExpect(status().isForbidden());
  var client=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").status(MemberStatus.ACTIVE).build(),Set.of(),false);
  mvc.perform(get("/finance/vouchers/receipts").with(user(client))).andExpect(status().isForbidden());
  mvc.perform(post("/finance/vouchers/receipts").with(user(actor)).with(csrf()).param("rows[999].amount","1.01")).andExpect(status().isBadRequest());verify(service,never()).post(any(),any(),any());
 }
 @Test void pdfAndPrintReauthorizeTheExactResource()throws Exception{
  var v=voucher();when(service.export(actor,Type.RECEIPT,id)).thenReturn(v);when(pdf.render(eq(v),any())).thenReturn(new VoucherPdfService.Output(new byte[]{1,2,3},"RV-2026-00000001.pdf"));
  mvc.perform(get("/finance/vouchers/receipts/"+id+"/pdf").with(user(actor))).andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andExpect(header().string("Cache-Control","no-store"));
  mvc.perform(get("/finance/vouchers/receipts/"+id+"/print").with(user(actor))).andExpect(view().name("accounting/vouchers/print"));verify(service,times(2)).export(actor,Type.RECEIPT,id);
 }
 Voucher voucher(){return new Voucher(id,"RV-2026-00000001",Type.RECEIPT,LocalDate.of(2026,10,8),"Payer","REF","","",UUID.randomUUID(),new BigDecimal("3.03"),2,UUID.randomUUID(),actor.getMemberId(),"Accountant",OffsetDateTime.now(),null,null,"Institution","Branch",List.of());}
 @Configuration @EnableWebMvc static class Config {
  @Bean("access") @Primary com.sacco.mvp.service.AccessControlService access(){return new com.sacco.mvp.service.AccessControlService();}
  @Bean VoucherService service(){return mock(VoucherService.class);}
  @Bean VoucherPdfService pdf(){return mock(VoucherPdfService.class);}
  @Bean ApplicationClock voucherClock(){var clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(LocalDate.of(2026,10,8));return clock;}
  @Bean VoucherController controller(VoucherService service,VoucherPdfService pdf,ApplicationClock voucherClock){return new VoucherController(service,pdf,voucherClock);}
 }
}
