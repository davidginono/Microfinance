package com.sacco.mvp.config;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.controller.*;
import com.sacco.mvp.grouplending.dto.GroupCoverageDtos.*;
import com.sacco.mvp.grouplending.dto.GroupLendingDtos.Page;
import com.sacco.mvp.grouplending.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.security.access.AccessDeniedException;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,GroupCoverageMvcTest.Config.class})
class GroupCoverageMvcTest {
 @Autowired WebApplicationContext context;@Autowired GroupCoverageService service;@Autowired GroupCoveragePdf pdf;
 MockMvc mvc;AppUserPrincipal staff,client;UUID id=UUID.randomUUID(),cycle=UUID.randomUUID();
 @BeforeEach void setup(){reset(service,pdf);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();staff=actor(true);client=actor(false);}
 AppUserPrincipal actor(boolean staff){return new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(staff?Position.ACCOUNTANT:Position.MEMBER).memberAccount(!staff).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),staff);}
 Detail detail(boolean staff){var date=LocalDate.of(2026,10,27);var amount=new BigDecimal("100.00");return new Detail(new Case(id,cycle,UUID.randomUUID(),UUID.randomUUID(),"PERSONAL",client.getMemberId(),"CLIENT-1","Named <Person>","Organization <Borrower>","LN-1",UUID.randomUUID(),date,date.plusDays(30),date.minusDays(15),amount,amount,amount,amount,amount,BigDecimal.ZERO,"CALLED",3,"Accepted terms","BOTH","HASH",1,date.atStartOfDay().atOffset(ZoneOffset.UTC)),new Page<>(List.of(),0,false),new Page<>(List.of(),0,false),staff);}
 String staffPath(){return "/finance/groups/coverage/"+id;}String ownPath(){return "/app/group-coverage/"+id;}
 @Test void allCoverageMutationsRequireCsrf()throws Exception{
  for(String path:List.of("/finance/groups/coverage?cycle="+cycle,"/finance/groups/coverage/policy?cycle="+cycle,staffPath()+"/close",staffPath()+"/link",staffPath()+"/correct",ownPath()+"/respond"))mvc.perform(post(path).with(user(path.startsWith("/app")?client:staff))).andExpect(status().isForbidden());verifyNoInteractions(service);
 }
 @Test void linkingUsesPathIdentityAndOnlyAllowedEvidenceFields()throws Exception{
  UUID posting=UUID.randomUUID(),contribution=UUID.randomUUID(),key=UUID.randomUUID();mvc.perform(post(staffPath()+"/link").with(user(staff)).with(csrf()).param("requestKey",key.toString()).param("postingId",posting.toString()).param("contributionId",contribution.toString()).param("payerEvidence","Verified retained payer evidence").param("payerVerified","true").param("amount","99999").param("clientId",UUID.randomUUID().toString()).param("cycle",UUID.randomUUID().toString())).andExpect(redirectedUrl(staffPath()));
  verify(service).link(eq(staff),eq(id),argThat(x->x.getPostingId().equals(posting)&&x.getContributionId().equals(contribution)&&x.getRequestKey().equals(key)&&x.isPayerVerified()));
 }
 @Test void ownResponseRetainsTextKeyAndOriginalSequenceAfterFailure()throws Exception{
  when(service.detail(client,id,0,0,true)).thenReturn(detail(false));when(service.actions(client,true)).thenReturn(Set.of("RESPOND"));when(service.respond(eq(client),eq(id),any())).thenThrow(new IllegalArgumentException("coverage.error.promise"));UUID key=UUID.randomUUID();
  var result=mvc.perform(post(ownPath()+"/respond").with(user(client)).with(csrf()).param("requestKey",key.toString()).param("expectedSequence","2").param("kind","PROMISED").param("amount","12.34").param("promiseOn","2026-11-02").param("reason","Keep my explanation <safe>").param("confirmed","true")).andExpect(view().name("groups/coverage-detail")).andExpect(model().attributeHasErrors("coverageResponse")).andReturn();
  var form=(ResponseForm)result.getModelAndView().getModel().get("coverageResponse");assertThat(form.getRequestKey()).isEqualTo(key);assertThat(form.getExpectedSequence()).isEqualTo(2);assertThat(form.getReason()).isEqualTo("Keep my explanation <safe>");assertThat(form.getAmount()).isEqualByComparingTo("12.34");
 }
 @Test void staffCannotSubmitPromiseFieldsThroughClosureBinder()throws Exception{
  mvc.perform(post(staffPath()+"/close").with(user(staff)).with(csrf()).param("kind","CLOSED").param("expectedSequence","3").param("reason","Close supported case").param("amount","1.00").param("promiseOn","2027-01-01").param("confirmed","true")).andExpect(redirectedUrl(staffPath()));verify(service).close(eq(staff),eq(id),argThat(x->x.getAmount()==null&&x.getPromiseOn()==null&&x.getKind().equals("CLOSED")));
 }
 @Test void ownershipDenialAndScopedDocumentsUseDistinctServiceModes()throws Exception{
  when(service.detail(client,id,0,0,true)).thenThrow(new AccessDeniedException("Unavailable"));mvc.perform(get(ownPath()).with(user(client))).andExpect(status().isForbidden());
  var own=detail(false);when(service.document(client,id,true)).thenReturn(own);when(pdf.render(eq(own),any())).thenReturn("%PDF-test".getBytes());mvc.perform(get(ownPath()+"/pdf").with(user(client))).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(content().contentType("application/pdf"));verify(service).document(client,id,true);
  when(service.notice(staff,id,false)).thenReturn("%PDF-notice".getBytes());mvc.perform(get(staffPath()+"/notice").with(user(staff))).andExpect(status().isOk()).andExpect(header().string("X-Content-Type-Options","nosniff"));verify(service).notice(staff,id,false);
 }
 @Test void originalLinkerFailureRedirectsWithTheSameReasonAndKey()throws Exception{
  UUID payment=UUID.randomUUID(),key=UUID.randomUUID();when(service.correct(staff,id,payment,key,"Retain correction reason")).thenThrow(new IllegalArgumentException("coverage.error.independent"));
  mvc.perform(post(staffPath()+"/correct").with(user(staff)).with(csrf()).param("payment",payment.toString()).param("requestKey",key.toString()).param("reason","Retain correction reason")).andExpect(redirectedUrl(staffPath())).andExpect(flash().attribute("coverageError","coverage.error.independent")).andExpect(flash().attribute("correctionReason","Retain correction reason")).andExpect(flash().attribute("correctionKey",key));
 }
 @Test void newViewLabelsAndStatesExistInBothLanguages()throws Exception{
  for(var locale:List.of(Locale.ENGLISH,Locale.of("sw"))){var bundle=ResourceBundle.getBundle("messages",locale);try(var files=java.nio.file.Files.list(java.nio.file.Path.of("src/main/webapp/WEB-INF/jsp/groups"))){for(var file:files.filter(p->p.getFileName().toString().startsWith("coverage-")).toList()){var match=java.util.regex.Pattern.compile("code=\"([^\"$]+)\"").matcher(java.nio.file.Files.readString(file));while(match.find())assertThat(bundle.containsKey(match.group(1))).as("%s %s",file,match.group(1)).isTrue();}}for(var state:List.of("CALLED","REQUESTED","ACKNOWLEDGED","PROMISED","DISPUTED","RECEIVED","PART_RECEIVED","CURED_ELSEWHERE","EXPIRED","CLOSED","CANCELLED","UNAVAILABLE"))assertThat(bundle.containsKey("coverage.state."+state)).isTrue();}
 }
 @Configuration @EnableWebMvc static class Config {
  @Bean GroupCoverageService coverage(){return mock(GroupCoverageService.class);}
  @Bean GroupCoveragePdf pdf(){return mock(GroupCoveragePdf.class);}
  @Bean GroupCoverageController staff(GroupCoverageService s){return new GroupCoverageController(s);}
  @Bean GroupCoverageClientController client(GroupCoverageService s){return new GroupCoverageClientController(s);}
  @Bean GroupCoverageDocumentController document(GroupCoverageService s,GroupCoveragePdf p){return new GroupCoverageDocumentController(s,p);}
 }
}
