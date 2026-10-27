package com.sacco.mvp.config;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.lending.controls.controller.CreditControlController;
import com.sacco.mvp.lending.controls.dto.CreditControlDtos.*;
import com.sacco.mvp.lending.controls.service.CreditControlService;
import com.sacco.mvp.security.AppUserPrincipal;
import org.junit.jupiter.api.*;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.*;
import org.springframework.test.context.junit.jupiter.web.SpringJUnitWebConfig;
import org.springframework.test.web.servlet.*;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.servlet.config.annotation.EnableWebMvc;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;
import static org.mockito.ArgumentMatchers.*;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.*;
import static org.springframework.security.test.web.servlet.setup.SecurityMockMvcConfigurers.springSecurity;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@SpringJUnitWebConfig({MvcSecurityTestConfiguration.class,CreditControlMvcTest.Config.class})
class CreditControlMvcTest {
    @Autowired WebApplicationContext context;@Autowired CreditControlService service;MockMvc mvc;AppUserPrincipal staff,client;UUID id=UUID.randomUUID(),loan=UUID.randomUUID();
    @BeforeEach void setup(){reset(service);mvc=MockMvcBuilders.webAppContextSetup(context).apply(springSecurity()).build();staff=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.ACCOUNTANT).staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),true);client=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).saccoId("I1").stationId("B1").position(Position.MEMBER).memberAccount(true).status(MemberStatus.ACTIVE).build(),EnumSet.allOf(UserClaim.class),false);when(service.guarantee(any(),any())).thenReturn(new Guarantee(id,loan,"L001","Borrower","Guarantor","PENDING","CLIENT/"+client.getMemberId(),new BigDecimal("1020.00"),null));when(service.officers(any(),anyString(),anyInt())).thenReturn(new Page<>(List.of(),0,false));}
    @Test void allCommandsRequireCsrfAndStaffSession()throws Exception{
        for(String path:List.of("policy","guarantors/"+id+"/assess","collateral/new","collateral/"+id+"/events/new","collections/new","collections/"+id+"/events/new"))mvc.perform(post("/finance/credit/"+path).with(user(staff))).andExpect(status().isForbidden());
        for(String path:List.of("guarantors","collateral","collections","policy","choices/officers"))mvc.perform(get("/finance/credit/"+path).with(user(client))).andExpect(status().isForbidden());verifyNoInteractions(service);
    }
    @Test void assessmentBinderCannotSetIdentityScopeOrApproval()throws Exception{
        mvc.perform(post("/finance/credit/guarantors/"+id+"/assess").with(user(staff)).with(csrf()).param("income","2000.00").param("expenses","300.00").param("otherDebt","0.00").param("capacity","1200.00").param("verified","true").param("evidence","Checked sources").param("policyReference","Approved method").param("guarantorId",staff.getMemberId().toString()).param("saccoId","FOREIGN").param("status","APPROVED")).andExpect(redirectedUrl("/finance/credit/guarantors/"+id));verify(service).assess(eq(staff),eq(id),argThat(f->f.getCapacity().toPlainString().equals("1200.00")&&f.isVerified()&&f.getEvidence().equals("Checked sources")));
    }
    @Test void invalidAssessmentRetainsValuesAndFieldErrors()throws Exception{
        when(service.assess(any(),eq(id),any())).thenThrow(new IllegalArgumentException("credit.error.income"));var result=mvc.perform(post("/finance/credit/guarantors/"+id+"/assess").with(user(staff)).with(csrf()).param("income","-10.25").param("evidence","Retain checked document reference")).andExpect(status().isOk()).andExpect(view().name("credit/form")).andExpect(model().attributeHasFieldErrors("assessmentForm","income")).andReturn();assertThat(((AssessmentForm)result.getModelAndView().getModel().get("assessmentForm")).getEvidence()).isEqualTo("Retain checked document reference");
    }
    @Test void collectionNotesCannotPostMoneyOrSetCaseState()throws Exception{
        mvc.perform(post("/finance/credit/collections/"+id+"/events/new").with(user(staff)).with(csrf()).param("kind","PROMISE").param("amount","100.25").param("promiseOn","2026-11-01").param("evidence","Client promised to pay").param("principalPaid","99999").param("state","PAID").param("actorId",client.getMemberId().toString())).andExpect(redirectedUrl("/finance/credit/collections/"+id));verify(service).followUp(eq(staff),eq(id),argThat(f->f.getKind().equals("PROMISE")&&f.getAmount().toPlainString().equals("100.25")));
    }
    @Test void serviceScopeFailuresDoNotExposeForeignRecords()throws Exception{when(service.asset(staff,id)).thenThrow(new org.springframework.security.access.AccessDeniedException("Unavailable"));mvc.perform(get("/finance/credit/collateral/"+id).with(user(staff))).andExpect(status().isForbidden());}
    @Test void allNewClaimsRequireExplicitAssignment(){for(var role:Position.values())for(var claim:UserClaim.values())if(Set.of(AccessFeature.CREDIT_CONTROLS,AccessFeature.GUARANTOR_CAPACITY,AccessFeature.COLLATERAL,AccessFeature.COLLECTIONS).contains(claim.getFeature()))assertThat(UserClaim.defaultClaims(Set.of(role),false)).doesNotContain(claim);}
    @Test void staticAndDynamicLabelsExistInBothLanguages()throws Exception{
        var keys=new HashSet<String>();try(var files=Files.list(Path.of("src/main/webapp/WEB-INF/jsp/credit"))){for(var file:files.toList()){var matcher=java.util.regex.Pattern.compile("code=[\"']([^\"'$]+)[\"']").matcher(Files.readString(file));while(matcher.find())keys.add(matcher.group(1));}}
        for(String field:List.of("loan","borrower","guarantor","liability","status","code","description","owner","value","pledged","expires","officer","arrears","oldest","next","state","income","expenses","otherDebt","capacity","assessedOn","validThrough","policyReference","evidence","actor","principal","dueInterest","futureInterest","charges"))keys.add("credit.field."+field);
        for(var locale:List.of(Locale.ENGLISH,Locale.of("sw"))){var bundle=ResourceBundle.getBundle("messages",locale);for(String key:keys)assertThat(bundle.containsKey(key)).as("%s %s",locale,key).isTrue();}
    }
    @Configuration @EnableWebMvc static class Config {@Bean CreditControlService service(){return mock(CreditControlService.class);}@Bean CreditControlController controller(CreditControlService service){return new CreditControlController(service);}}
}
