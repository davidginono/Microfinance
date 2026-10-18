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
@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,VoucherMvcTest.Config.class})
class VoucherMvcTest {
 @Autowired WebApplicationContext context;@Autowired VoucherService service;@Autowired VoucherPdfService pdf;@Autowired LoanReceiptService receipts;@Autowired LoanReceiptPdfService loanPdf;
 @Autowired com.sacco.mvp.accounting.approval.service.VoucherApprovalGate gate;
 @Autowired com.sacco.mvp.accounting.approval.service.VoucherWorkflowService workflows;
 @Autowired com.sacco.mvp.accounting.approval.service.VoucherWorkflowPostingService workflowPosting;
 @Autowired com.sacco.mvp.accounting.approval.service.VoucherDocumentService documents;
 MockMvc mvc;AppUserPrincipal actor;UUID id=UUID.randomUUID();
 @BeforeEach void setup(){
  reset(service,pdf,receipts,loanPdf,gate,workflows,workflowPosting,documents);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();
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
 @Test void retiredReportGrantsCannotOpenVoucherRoutesAndRevokedCreateCannotPost()throws Exception{
  var member=Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").memberNo("SYNTHETIC")
   .position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build();
  var retired=new AppUserPrincipal(member,Set.of(UserClaim.REPORT_RUN,UserClaim.FINANCIAL_REPORTS_FINALIZE),true);
  var repository=mock(com.sacco.mvp.accounting.repository.VoucherRepository.class);
  var directory=mock(com.sacco.mvp.service.MemberDirectoryService.class);
  var ledger=new GeneralLedgerService(mock(com.sacco.mvp.accounting.repository.GeneralLedgerRepository.class),
   new com.sacco.mvp.service.AccessControlService(),mock(com.sacco.mvp.service.AuditService.class),context.getBean(ApplicationClock.class),
   mock(com.sacco.mvp.service.UserClaimService.class),directory,mock(com.sacco.mvp.service.SaccoRegistryService.class));
  var realService=new VoucherService(repository,ledger,context.getBean(ApplicationClock.class),mock(com.sacco.mvp.service.AuditService.class),tools.jackson.databind.json.JsonMapper.builder().build(),gate);
  var controller=context.getBean(VoucherController.class);
  org.springframework.test.util.ReflectionTestUtils.setField(controller,"vouchers",realService);
  try{
   mvc.perform(get("/finance/vouchers/journals").with(user(retired))).andExpect(status().isForbidden());
   var readOnly=new AppUserPrincipal(member,Set.of(UserClaim.ACCOUNTING_JOURNALS_VIEW),true);
   mvc.perform(post("/finance/vouchers/journals").with(user(readOnly)).with(csrf())).andExpect(status().isForbidden());
   verifyNoInteractions(repository,directory,receipts,gate,workflows,workflowPosting);
  }finally{org.springframework.test.util.ReflectionTestUtils.setField(controller,"vouchers",service);}
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
  var v=voucher();when(service.export(actor,Type.RECEIPT,id)).thenReturn(v);when(pdf.render(eq(v),any(),isNull())).thenReturn(new VoucherPdfService.Output(new byte[]{1,2,3},"RV-2026-00000001.pdf"));
  mvc.perform(get("/finance/vouchers/receipts/"+id+"/pdf").with(user(actor))).andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andExpect(header().string("Cache-Control","no-store"));
  mvc.perform(get("/finance/vouchers/receipts/"+id+"/print").with(user(actor))).andExpect(status().isOk()).andExpect(content().contentType("application/pdf")).andExpect(header().string("Content-Disposition","inline; filename=RV-2026-00000001.pdf")).andExpect(header().string("Cache-Control","no-store"));verify(service,times(2)).export(actor,Type.RECEIPT,id);verify(pdf,times(2)).render(eq(v),any(),isNull());
 }
 Voucher voucher(){return new Voucher(id,"RV-2026-00000001",Type.RECEIPT,LocalDate.of(2026,10,8),"Payer","REF","","",UUID.randomUUID(),new BigDecimal("3.03"),2,UUID.randomUUID(),actor.getMemberId(),"Accountant",OffsetDateTime.now(),null,null,"Institution","Branch",List.of());}
 @Test void loanReceiptUsesRepaymentUseCaseAndNotGenericJournalPosting()throws Exception{
  var receipt=new LoanReceipt(voucher(),"C100",List.of());when(receipts.post(eq(actor),any())).thenReturn(receipt);when(receipts.selected(eq(actor),any())).thenReturn(Map.of());
  mvc.perform(post("/finance/vouchers/receipts").with(user(actor)).with(csrf()).param("receiptPurpose","LOAN_REPAYMENT").param("clientId",UUID.randomUUID().toString()).param("rows[0].loanId",UUID.randomUUID().toString()).param("rows[0].amount","120.00").param("rows[0].description","Repayment")).andExpect(redirectedUrl("/finance/vouchers/receipts/"+id));
  verify(receipts).post(eq(actor),argThat(f->f.getClientId()!=null && f.getRows().getFirst().getLoanId()!=null));verify(service,never()).post(any(),any(),any());
 }
 @Test void loanPreviewAndErrorsPreserveTheConditionalForm()throws Exception{
  when(receipts.preview(eq(actor),any())).thenThrow(new IllegalArgumentException("repayment.error.advance"));when(receipts.selected(eq(actor),any())).thenReturn(Map.of());
  mvc.perform(post("/finance/vouchers/receipts").with(user(actor)).with(csrf()).param("receiptPurpose","LOAN_REPAYMENT").param("action","preview").param("rows[0].amount","9999.00")).andExpect(view().name("accounting/vouchers/form")).andExpect(model().attributeHasErrors("voucherForm"));
  verify(receipts,never()).post(any(),any());verify(service,never()).preview(any(),any(),any());
 }
 @Test void loanPdfAndPrintUseProtectedCombinedDocument()throws Exception{
  var receipt=new LoanReceipt(voucher(),"C100",List.of());when(receipts.find(actor,id)).thenReturn(Optional.of(receipt));when(receipts.export(actor,id)).thenReturn(receipt);when(loanPdf.render(eq(receipt),any(),isNull())).thenReturn(new byte[]{1,2,3});
  mvc.perform(get("/finance/vouchers/receipts/"+id+"/print").with(user(actor))).andExpect(content().contentType("application/pdf")).andExpect(header().string("Content-Disposition","inline; filename=RV-2026-00000001.pdf"));verify(receipts).export(actor,id);verifyNoInteractions(pdf);
  when(receipts.export(actor,id)).thenThrow(new org.springframework.security.access.AccessDeniedException("Export revoked"));mvc.perform(get("/finance/vouchers/receipts/"+id+"/pdf").with(user(actor))).andExpect(status().isForbidden());
 }
 @Test void printCannotBypassResourceExportAuthorization()throws Exception {when(service.export(actor,Type.RECEIPT,id)).thenThrow(new org.springframework.security.access.AccessDeniedException("Outside branch"));mvc.perform(get("/finance/vouchers/receipts/"+id+"/print").with(user(actor))).andExpect(status().isForbidden());verifyNoInteractions(pdf);}
 @Test void configuredIndividualFormSavesApprovalDraftWithoutPosting()throws Exception{
  UUID workflow=UUID.randomUUID();when(gate.required(actor,Type.JOURNAL)).thenReturn(true);when(workflows.capture(eq(actor),eq(Type.JOURNAL),any())).thenReturn(workflow);
  mvc.perform(post("/finance/vouchers/journals").with(user(actor)).with(csrf()).param("party","Prepared voucher").param("rows[0].amount","1.00"))
   .andExpect(redirectedUrl("/finance/approval/"+workflow));verify(service,never()).post(any(),any(),any());
 }
 @Test void approvalCommandsRequireCsrfAndPassOnlyTheirScopedRecordContext()throws Exception{
  mvc.perform(post("/finance/approval/"+id+"/actions").with(user(actor)).param("version","2").param("action","APPROVE")).andExpect(status().isForbidden());
  mvc.perform(post("/finance/approval/"+id+"/actions").with(user(actor)).with(csrf()).param("version","2").param("action","REJECT").param("comments","Wrong reference").param("saccoId","FOREIGN"))
   .andExpect(redirectedUrl("/finance/approval/"+id));verify(workflows).action(actor,id,2,"REJECT","Wrong reference");
  doThrow(new org.springframework.security.access.AccessDeniedException("Outside branch")).when(workflows).details(actor,id);
  mvc.perform(get("/finance/approval/"+id).with(user(actor))).andExpect(status().isForbidden());
 }
 @Test void ruleBindingIsBoundedAndDoesNotAcceptApprovalEvidence()throws Exception{
  mvc.perform(post("/finance/approval/configuration").with(user(actor)).with(csrf()).param("type","PAYMENT").param("rules[0].name","Office payments").param("rules[0].minimum","0").param("individuals","true").param("approvedBy",UUID.randomUUID().toString()))
   .andExpect(redirectedUrl("/finance/approval/configuration?type=PAYMENT"));
  verify(workflows).configure(eq(actor),argThat(f->f.isIndividuals() && f.getRules().size()==1 && f.getType()==Type.PAYMENT));
  mvc.perform(post("/finance/approval/configuration").with(user(actor)).with(csrf()).param("rules[999].name","Overflow")).andExpect(status().isBadRequest());
 }
 @Test void anIndividualEditorCannotSubmitItsParentBatch()throws Exception{
  var details=mock(com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.Details.class);var workflow=mock(com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.Workflow.class);
  when(details.workflow()).thenReturn(workflow);when(workflow.scope()).thenReturn("BATCH");UUID itemId=UUID.randomUUID();var item=mock(com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.Item.class);when(item.id()).thenReturn(itemId);when(details.items()).thenReturn(List.of(item));when(details.actions()).thenReturn(Set.of("EDIT"));when(workflows.details(actor,id)).thenReturn(details);
  mvc.perform(post("/finance/approval/"+id+"/vouchers/"+itemId).with(user(actor)).with(csrf()).param("version","1").param("itemVersion","1").param("command","SUBMIT"))
   .andExpect(status().isOk()).andExpect(view().name("accounting/approval/editor"));
  verify(workflows,never()).save(any(),any(),any(),anyInt(),anyInt(),any());verify(workflows,never()).action(any(),any(),anyInt(),any(),any());
 }
 @Test void configurationValidateDoesNotSaveTheRules()throws Exception{
  mvc.perform(post("/finance/approval/configuration").with(user(actor)).with(csrf()).param("type","JOURNAL").param("command","VALIDATE").param("rules[0].name","Review").param("rules[0].minimum","0"))
   .andExpect(status().isOk()).andExpect(view().name("accounting/approval/configuration")).andExpect(model().attribute("configurationValid",true));
  verify(workflows).validateConfiguration(eq(actor),any());verify(workflows,never()).configure(any(),any());
 }
 @Test void standaloneApprovalCreationAndItemRemovalEndpointsAreUnavailable()throws Exception{
  mvc.perform(get("/finance/approval/new").with(user(actor))).andExpect(status().is4xxClientError());
  mvc.perform(post("/finance/approval").with(user(actor)).with(csrf()).param("type","JOURNAL").param("scope","BATCH").param("title","Removed creation flow")).andExpect(status().isMethodNotAllowed());
  mvc.perform(post("/finance/approval/"+id+"/vouchers/"+UUID.randomUUID()+"/delete").with(user(actor)).with(csrf()).param("version","1")).andExpect(status().isNotFound());
  verifyNoInteractions(workflows,workflowPosting);
 }
 @Test void correctionRoutesCannotCreateAnUnknownVoucher()throws Exception{
  when(workflows.details(actor,id)).thenReturn(new com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.Details(null,List.of(),List.of(),Set.of("EDIT")));
  UUID item=UUID.randomUUID();
  mvc.perform(get("/finance/approval/"+id+"/vouchers/"+item).with(user(actor))).andExpect(status().isNotFound());
  mvc.perform(post("/finance/approval/"+id+"/vouchers/"+item).with(user(actor)).with(csrf()).param("version","1").param("itemVersion","0").param("party","Forged creation")).andExpect(status().isNotFound());
  verify(workflows,never()).save(any(),any(),any(),anyInt(),anyInt(),any());verifyNoInteractions(workflowPosting);
 }
 @Test void supportingDocumentUsesRecordAuthorizationAndRestrictiveHeaders()throws Exception{
  UUID document=UUID.randomUUID();when(documents.get(actor,id,document)).thenReturn(new com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.Document(document,"evidence.pdf","application/pdf",new byte[]{1,2,3}));
  mvc.perform(get("/finance/approval/"+id+"/documents/"+document).with(user(actor))).andExpect(status().isOk()).andExpect(content().contentType("application/pdf"))
   .andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("X-Content-Type-Options","nosniff"))
   .andExpect(header().string("Content-Security-Policy","sandbox; default-src 'none'; frame-ancestors 'self'"));
  doThrow(new org.springframework.security.access.AccessDeniedException("Claim revoked")).when(documents).get(actor,id,document);
  mvc.perform(get("/finance/approval/"+id+"/documents/"+document).with(user(actor))).andExpect(status().isForbidden());
 }
 @Configuration @EnableWebMvc static class Config {
  @Bean("access") @Primary com.sacco.mvp.service.AccessControlService access(){return new com.sacco.mvp.service.AccessControlService();}
  @Bean VoucherService service(){return mock(VoucherService.class);}
  @Bean VoucherPdfService pdf(){return mock(VoucherPdfService.class);}
  @Bean ApplicationClock voucherClock(){var clock=mock(ApplicationClock.class);when(clock.today()).thenReturn(LocalDate.of(2026,10,8));return clock;}
  @Bean LoanReceiptService receipts(){return mock(LoanReceiptService.class);}
  @Bean LoanReceiptPdfService loanPdf(){return mock(LoanReceiptPdfService.class);}
  @Bean com.sacco.mvp.accounting.approval.service.VoucherApprovalGate gate(){return mock(com.sacco.mvp.accounting.approval.service.VoucherApprovalGate.class);}
  @Bean com.sacco.mvp.accounting.approval.service.VoucherWorkflowService workflows(){return mock(com.sacco.mvp.accounting.approval.service.VoucherWorkflowService.class);}
  @Bean com.sacco.mvp.accounting.approval.service.VoucherWorkflowPostingService workflowPosting(){return mock(com.sacco.mvp.accounting.approval.service.VoucherWorkflowPostingService.class);}
  @Bean com.sacco.mvp.accounting.approval.service.VoucherDocumentService documents(){return mock(com.sacco.mvp.accounting.approval.service.VoucherDocumentService.class);}
  @Bean com.sacco.mvp.accounting.approval.controller.VoucherWorkflowController workflowController(com.sacco.mvp.accounting.approval.service.VoucherWorkflowService workflows,com.sacco.mvp.accounting.approval.service.VoucherWorkflowPostingService posting,com.sacco.mvp.accounting.approval.service.VoucherDocumentService documents){return new com.sacco.mvp.accounting.approval.controller.VoucherWorkflowController(workflows,posting,documents);}
  @Bean VoucherController controller(VoucherService service,VoucherPdfService pdf,ApplicationClock voucherClock,LoanReceiptService receipts,LoanReceiptPdfService loanPdf,com.sacco.mvp.accounting.approval.service.VoucherApprovalGate gate,com.sacco.mvp.accounting.approval.service.VoucherWorkflowService workflows){return new VoucherController(service,pdf,voucherClock,mock(com.sacco.mvp.service.SaccoLogoStorageService.class),receipts,loanPdf,gate,workflows);}
 }
}
