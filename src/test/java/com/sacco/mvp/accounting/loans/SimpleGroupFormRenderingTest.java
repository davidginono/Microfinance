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
    @Test void compactRecordingAndReadOnlyEvidenceRenderInBothLanguages() throws Exception {
        try(var fragments=Files.list(Path.of("src/main/webapp/WEB-INF/jsp/accounting/loans"))){
            for(var file:fragments.filter(path->path.toString().endsWith(".jspf")).toList())Files.copy(file,temporary.resolve(file.getFileName()));
        }
        Files.createDirectories(temporary.resolve("WEB-INF"));
        Files.copy(Path.of("src/main/webapp/WEB-INF/application-time.tld"),temporary.resolve("WEB-INF/application-time.tld"));
        String recording=Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/accounting/loans/record-form.jsp"));
        int labels=recording.indexOf("<form:form");
        String formOpening=recording.substring(labels,recording.indexOf("<form:hidden",labels));
        Files.writeString(temporary.resolve("form.jsp"),"""
            <%@ page pageEncoding="UTF-8" %>
            <%@ taglib prefix="c" uri="jakarta.tags.core" %>
            <%@ taglib prefix="appTime" uri="https://sacco.local/tags/time" %>
            <%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
            <%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
            <%@ taglib prefix="sec" uri="http://www.springframework.org/security/tags" %>
            """+formOpening+"""
            <div id="recording-panel-loan"><%@ include file="simple-group-fields.jspf" %></div>
            <%@ include file="recording-evidence-panels.jspf" %></form:form>
            <!--details--><%@ include file="simple-group-details.jspf" %>
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
                request.setAttribute("recordForm",form);request.setAttribute("groupSetup",new Setup(group,1,"Group",first,"Asha","BANK_ACCOUNT","Bank","Group","123","Checked",List.of(new Person(first,"C1","Asha"),new Person(second,"C2","Juma"))));
                request.setAttribute("saveUrl","/record");
                request.setAttribute("simpleGroup",new Contract(UUID.randomUUID(),group,"Group","Asha","Group bank","Agreement","EN",true,List.of(new Borrower(first,"C1","Asha",form.getGroupApplication().getSignedOn(),java.math.BigDecimal.TEN,java.math.BigDecimal.ZERO,"CLIENT_PROFILE:"+UUID.randomUUID()),new Borrower(second,"C2","Juma",form.getGroupApplication().getSignedOn(),java.math.BigDecimal.TEN,java.math.BigDecimal.ZERO,"Historical budget reference"))));
                request.getRequestDispatcher("/form.jsp").forward(request,response);
            }
        });context.addServletMappingDecoded("/form","form");
        try {
            tomcat.start();var client=HttpClient.newHttpClient();
            for(String language:List.of("en","sw")){
                var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/form")).header("Accept-Language",language).timeout(Duration.ofSeconds(60)).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);String body=response.body();
                String entry=body.substring(0,body.indexOf("<!--details-->"));
                assertThat(entry).doesNotContain("<spring:message","&lt;spring:message","firstPaymentDate",".incomeSource",".signers[","data-group-roster","simple-group-borrower","data-profile-label","groupApplication.reference","groupApplication.language");
                assertThat(entry.split("name=\"groupApplication.signedOn\"",-1)).hasSize(2);
                assertThat(entry).contains("2026-11-12","groupAgreement");
                String loan=entry.substring(0,entry.indexOf("id=\"recording-panel-guarantors\""));
                String collateral=entry.substring(entry.indexOf("id=\"recording-panel-collateral\""),entry.indexOf("id=\"recording-panel-attachments\""));
                assertThat(loan).doesNotContain("groupAgreement","groupApplication.checked",messages.getMessage("simpleGroup.liability",null,Locale.forLanguageTag(language)));
                assertThat(loan).contains("class=\"group-debt-content\"",messages.getMessage("simpleGroup.debtHelp",null,Locale.forLanguageTag(language)));
                assertThat(entry).contains(messages.getMessage("simpleGroup.extraGuarantorsHelp",null,Locale.forLanguageTag(language)));
                assertThat(collateral).contains("data-group-confirmation","name=\"groupApplication.checked\"",messages.getMessage("simpleGroup.liability",null,Locale.forLanguageTag(language)),messages.getMessage("simpleGroup.checked",null,Locale.forLanguageTag(language)));
                assertThat(collateral.indexOf("data-group-confirmation")).isGreaterThan(collateral.indexOf("data-add-row=\"collateral\""));
                String attachments=entry.substring(entry.indexOf("id=\"recording-panel-attachments\""));
                assertThat(attachments).contains("name=\"groupAgreement\"","attachment-dropzone-input","data-group-agreement-selected","data-remove-group-agreement","M6 6l12 12M18 6L6 18");
                assertThat(attachments).contains(messages.getMessage("simpleGroup.agreementHelp",null,Locale.forLanguageTag(language)));
                assertThat(body).contains("Historical budget reference").doesNotContain("CLIENT_PROFILE:");
            }
        }finally{tomcat.stop();tomcat.destroy();spring.close();}
    }
}
