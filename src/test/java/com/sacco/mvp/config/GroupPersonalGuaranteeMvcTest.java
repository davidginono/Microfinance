package com.sacco.mvp.config;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.grouplending.controller.*;
import com.sacco.mvp.grouplending.dto.GroupPersonalGuaranteeDtos.Form;
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
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;
@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,GroupPersonalGuaranteeMvcTest.Config.class})
class GroupPersonalGuaranteeMvcTest {
 @Autowired WebApplicationContext context;@Autowired GroupPersonalGuaranteeService service;@Autowired GroupPersonalGuaranteeDeliveryService delivery;@Autowired GroupPersonalGuaranteePdf pdf;
 MockMvc mvc;AppUserPrincipal staff,client;UUID cycle=UUID.randomUUID(),offer=UUID.randomUUID();
 String staffPath(){return "/finance/groups/cycles/"+cycle+"/personal-guarantees";}String ownPath(){return "/app/group-guarantees/"+offer;}
 @BeforeEach void setup(){reset(service,delivery,pdf);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();staff=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),true);client=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.MEMBER).memberAccount(true).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),false);}
 @Test void financialStaffCommandsRequireCsrfAndUseAllowlistedFields()throws Exception{
  mvc.perform(post(staffPath()).with(user(staff))).andExpect(status().isForbidden());verifyNoInteractions(service);
  when(service.record(eq(staff),eq(cycle),any(),isNull())).thenReturn(offer);mvc.perform(post(staffPath()).with(user(staff)).with(csrf()).param("clientId",client.getMemberId().toString()).param("capacity","2000.00").param("capacityEvidence","Retained evidence").param("validThrough","2027-01-01").param("saccoId","FOREIGN").param("cap","1.00").param("decision","ACCEPTED").param("document.documentChecked","true")).andExpect(status().is3xxRedirection()).andExpect(redirectedUrl(staffPath()+"/"+offer));verify(service).record(eq(staff),eq(cycle),argThat(f->f.getClientId().equals(client.getMemberId())&&f.getCapacity().toPlainString().equals("2000.00")&&f.getDocument().isDocumentChecked()),isNull());
 }
 @Test void personallyBoundAcceptanceRefusalAndCodeCannotBypassCsrfOrOwner()throws Exception{
  for(var action:List.of("accept","refuse","code","document"))mvc.perform(post(ownPath()+"/"+action).with(user(client))).andExpect(status().isForbidden());verifyNoInteractions(service,delivery);
  mvc.perform(post(ownPath()+"/accept").with(user(client)).with(csrf()).param("packetHash","HASH").param("accepted","true").param("otpCode","123456").param("clientId",UUID.randomUUID().toString())).andExpect(status().is3xxRedirection());verify(service).accept(client,offer,"HASH",true,"123456");mvc.perform(post(ownPath()+"/refuse").with(user(client)).with(csrf()).param("packetHash","HASH").param("reason","No consent")).andExpect(status().is3xxRedirection());verify(service).refuse(client,offer,"HASH","No consent");
 }
 @Test void clientCannotEnterStaffRoutesAndRevokedOwnerIsDenied()throws Exception{
  mvc.perform(get(staffPath()).with(user(client))).andExpect(status().isForbidden());verifyNoInteractions(service);when(service.packet(client,offer)).thenThrow(new org.springframework.security.access.AccessDeniedException("Unavailable"));mvc.perform(get(ownPath()).with(user(client))).andExpect(status().isForbidden());mvc.perform(get(ownPath())).andExpect(status().is3xxRedirection());
 }
 @Test void sourceDocumentIsPrivateAndOnlyPersonalPostRecordsOpening()throws Exception{
  when(service.clientDocument(client,offer)).thenReturn("%PDF-source".getBytes());mvc.perform(post(ownPath()+"/document").with(user(client)).with(csrf())).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(content().contentType("application/pdf"));mvc.perform(get(ownPath()+"/document").with(user(client))).andExpect(status().isMethodNotAllowed());verify(service,times(1)).clientDocument(client,offer);
 }
 @Test void signedEvidenceExportUsesOwnerScopeAndPrivatePdfPrintHeaders()throws Exception{
  var data=mock(com.sacco.mvp.grouplending.dto.GroupPersonalGuaranteeDtos.Evidence.class);when(service.evidence(client,null,offer,true)).thenReturn(data);when(pdf.render(data,Locale.ENGLISH)).thenReturn("%PDF-evidence".getBytes());for(var format:List.of("pdf","print"))mvc.perform(get(ownPath()+"/evidence/"+format).with(user(client)).locale(Locale.ENGLISH)).andExpect(status().isOk()).andExpect(header().string("Cache-Control","no-store")).andExpect(header().string("Content-Disposition",(format.equals("pdf")?"attachment":"inline")+"; filename=signed-personal-guarantee.pdf")).andExpect(content().contentType("application/pdf"));verify(service,times(2)).evidence(client,null,offer,true);
 }
 @Test void otpDeliveryRetainsTypedLoanAndNamedRecipientWithoutPersonalApplicant(){
  var transport=mock(com.sacco.mvp.service.StationOtpDeliveryService.class);var messages=new org.springframework.context.support.StaticMessageSource();messages.addMessage("personalGuarantee.otpSubject",Locale.ENGLISH,"Synthetic subject");messages.addMessage("personalGuarantee.otpIntro",Locale.ENGLISH,"Synthetic intro");UUID loan=UUID.randomUUID(),challenge=UUID.randomUUID();var data=new com.sacco.mvp.grouplending.dto.GroupPersonalGuaranteeDtos.Delivery(challenge,offer,loan,client.getMemberId(),"I1","B1","synthetic@example.invalid","","123456",10);when(service.prepareCode(client,offer,"HASH")).thenReturn(data);new GroupPersonalGuaranteeDeliveryService(service,transport,messages).send(client,offer,"HASH",Locale.ENGLISH);verify(transport).deliver("I1","B1","synthetic@example.invalid","",EmailOtpPurpose.GUARANTOR_SIGNATURE,"Synthetic subject","Synthetic intro","123456",10,loan,null,client.getMemberId());verify(service).codeDelivered(client,offer,challenge);
 }
 @Test void allNewLabelsAndDynamicStatesExistInBothLanguages()throws Exception{
  for(var locale:List.of(Locale.ENGLISH,Locale.of("sw"))){var bundle=ResourceBundle.getBundle("messages",locale);for(var file:List.of("personal-guarantees.jsp","personal-guarantees-client.jsp","personal-guarantee.jsp","personal-guarantee-form.jsp","personal-guarantee-source.jspf")){var text=java.nio.file.Files.readString(java.nio.file.Path.of("src/main/webapp/WEB-INF/jsp/groups",file));var matcher=java.util.regex.Pattern.compile("code=\"([^\"$]+)\"").matcher(text);while(matcher.find())assertThat(bundle.containsKey(matcher.group(1))).as("%s %s",locale,matcher.group(1)).isTrue();}for(var state:List.of("PENDING","ACCEPTED","REFUSED","CANCELLED","EXPIRED"))assertThat(bundle.containsKey("personalGuarantee.state."+state)).isTrue();}
 }
 @Configuration @EnableWebMvc static class Config {
  @Bean GroupPersonalGuaranteeService guarantees(){return mock(GroupPersonalGuaranteeService.class);}
  @Bean GroupPersonalGuaranteeDeliveryService delivery(){return mock(GroupPersonalGuaranteeDeliveryService.class);}
  @Bean GroupPersonalGuaranteePdf pdf(){return mock(GroupPersonalGuaranteePdf.class);}
  @Bean GroupPersonalGuaranteeDocumentController documents(GroupPersonalGuaranteeService s,GroupPersonalGuaranteePdf p){return new GroupPersonalGuaranteeDocumentController(s,p,new com.sacco.mvp.service.ReportExportLimiter(2));}
  @Bean GroupPersonalGuaranteeController staff(GroupPersonalGuaranteeService s){return new GroupPersonalGuaranteeController(s);}
  @Bean GroupPersonalGuaranteeClientController client(GroupPersonalGuaranteeService s,GroupPersonalGuaranteeDeliveryService d){return new GroupPersonalGuaranteeClientController(s,d);}
 }
}
