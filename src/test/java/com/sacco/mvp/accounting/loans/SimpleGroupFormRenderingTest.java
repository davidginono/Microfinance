package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.RecordForm;
import com.sacco.mvp.grouplending.dto.SimpleGroupLoanDtos.*;
import jakarta.servlet.http.*;
import org.apache.catalina.startup.Tomcat;
import org.apache.jasper.servlet.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.assertThat;

class SimpleGroupFormRenderingTest {
    @TempDir Path temporary;
    @Test void commonDateAndOnlyMissingInputsRenderInBothLanguages() throws Exception {
        for(String name:List.of("simple-group-fields.jspf","simple-group-details.jspf","choice-search.jspf"))Files.copy(Path.of("src/main/webapp/WEB-INF/jsp/accounting/loans",name),temporary.resolve(name));
        String recording=Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/accounting/loans/record-form.jsp"));
        int labels=recording.indexOf("<spring:message code=\"simpleGroup.leader\"");
        String formOpening=recording.substring(labels,recording.indexOf("<form:hidden",labels));
        Files.writeString(temporary.resolve("form.jsp"),"""
            <%@ page pageEncoding="UTF-8" %>
            <%@ taglib prefix="c" uri="jakarta.tags.core" %>
            <%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
            <%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
            <%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
            """+formOpening+"""
            <%@ include file="simple-group-fields.jspf" %></form:form>
            <%@ include file="simple-group-details.jspf" %>
            """);
        var tomcat=new Tomcat();tomcat.setBaseDir(temporary.resolve("tomcat").toString());tomcat.setPort(0);tomcat.getConnector().setProperty("address","127.0.0.1");
        var context=tomcat.addContext("",temporary.toString());context.setParentClassLoader(getClass().getClassLoader());context.addServletContainerInitializer(new JasperInitializer(),null);
        var spring=new GenericWebApplicationContext();var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");
        spring.getBeanFactory().registerSingleton("messageSource",messages);spring.getBeanFactory().registerSingleton("access",new com.sacco.mvp.service.AccessControlService());
        context.addServletContainerInitializer((classes,servletContext)->{spring.setServletContext(servletContext);spring.refresh();servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE,spring);},null);
        Tomcat.addServlet(context,"jsp",new JspServlet());context.addServletMappingDecoded("*.jsp","jsp");
        UUID group=UUID.randomUUID(),first=UUID.randomUUID(),second=UUID.randomUUID();
        Tomcat.addServlet(context,"form",new HttpServlet(){
            @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws java.io.IOException,jakarta.servlet.ServletException {
                var form=new RecordForm();form.setLoanFor("GROUP");form.getGroupApplication().setGroupId(group);form.getGroupApplication().setSignedOn(java.time.LocalDate.of(2026,11,12));
                for(var id:List.of(first,second)){var signer=new SignerForm();signer.setClientId(id);signer.setIncomeSource("Retained evidence reference");form.getGroupApplication().getSigners().add(signer);}
                request.setAttribute("recordForm",form);request.setAttribute("groupSetup",new Setup(group,1,"Group",first,"Asha","BANK_ACCOUNT","Bank","Group","123","Checked",List.of(new Person(first,"C1","Asha"),new Person(second,"C2","Juma"))));
                request.setAttribute("saveUrl","/record");
                boolean complete="true".equals(request.getParameter("complete"));request.setAttribute("groupBorrowerInputs",Map.of(first,new BorrowerInput(false,List.of()),second,new BorrowerInput(!complete,complete?List.of():List.of("monthlyExpenses"))));
                request.setAttribute("simpleGroup",new Contract(UUID.randomUUID(),group,"Group","Asha","Group bank","Agreement","EN",List.of(new Borrower(first,"C1","Asha",form.getGroupApplication().getSignedOn(),java.math.BigDecimal.TEN,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,"CLIENT_PROFILE:"+UUID.randomUUID()),new Borrower(second,"C2","Juma",form.getGroupApplication().getSignedOn(),java.math.BigDecimal.TEN,java.math.BigDecimal.ZERO,java.math.BigDecimal.ZERO,"Historical budget reference"))));
                request.getRequestDispatcher("/form.jsp").forward(request,response);
            }
        });context.addServletMappingDecoded("/form","form");
        try {
            tomcat.start();var client=HttpClient.newHttpClient();
            for(String language:List.of("en","sw"))for(boolean complete:List.of(false,true)){
                var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/form?complete="+complete)).header("Accept-Language",language).timeout(Duration.ofSeconds(60)).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);String body=response.body();
                assertThat(body).contains("data-leader-label=\""+messages.getMessage("simpleGroup.leader",null,Locale.forLanguageTag(language))+"\"",
                    "data-source-label=\""+messages.getMessage("simpleGroup.incomeSource",null,Locale.forLanguageTag(language))+"\"")
                    .doesNotContain("<spring:message","&lt;spring:message");
                assertThat(body.split("name=\"groupApplication.signedOn\"",-1)).hasSize(2);assertThat(body).contains("2026-11-12").doesNotContain("signers[0].signedOn","signers[1].signedOn","signers[0].evidence","signers[1].evidence","signers[0].incomeSource");
                assertThat(body).contains("Historical budget reference").doesNotContain("CLIENT_PROFILE:");
                if(complete)assertThat(body).doesNotContain("signers[1].incomeSource");else assertThat(body).contains("signers[1].incomeSource","Retained evidence reference",language.equals("en")?"Monthly expenses":"Matumizi");
            }
        }finally{tomcat.stop();tomcat.destroy();spring.close();}
    }
}
