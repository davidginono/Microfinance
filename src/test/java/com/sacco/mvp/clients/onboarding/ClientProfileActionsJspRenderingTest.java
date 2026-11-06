package com.sacco.mvp.clients.onboarding;

import jakarta.servlet.http.*;
import org.apache.catalina.startup.Tomcat;
import org.apache.jasper.servlet.JasperInitializer;
import org.apache.jasper.servlet.JspServlet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

class ClientProfileActionsJspRenderingTest {
    @TempDir Path temporary;

    @Test void staffCreationOmitsDraftWhileExistingProfileActionsRemainAvailable() throws Exception {
        Files.copy(Path.of("src/main/webapp/WEB-INF/jsp/onboarding/profile-actions.jspf"),temporary.resolve("actions.jspf"));
        Files.writeString(temporary.resolve("actions.jsp"),"""
            <%@ page pageEncoding="UTF-8" %>
            <%@ taglib prefix="c" uri="jakarta.tags.core" %>
            <%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
            <form method="post"><input type="hidden" name="_csrf" value="test-token"/>
            <%@ include file="actions.jspf" %></form>
            """);
        var tomcat=new Tomcat();tomcat.setBaseDir(temporary.resolve("tomcat").toString());tomcat.setPort(0);
        tomcat.getConnector().setProperty("address","127.0.0.1");
        var context=tomcat.addContext("",temporary.toString());context.setParentClassLoader(getClass().getClassLoader());
        context.addServletContainerInitializer(new JasperInitializer(),null);
        var spring=new GenericWebApplicationContext();var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");
        spring.getBeanFactory().registerSingleton("messageSource",messages);
        context.addServletContainerInitializer((classes,servletContext)->{spring.setServletContext(servletContext);spring.refresh();servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE,spring);},null);
        Tomcat.addServlet(context,"jsp",new JspServlet());context.addServletMappingDecoded("*.jsp","jsp");
        Tomcat.addServlet(context,"actions",new HttpServlet(){
            @Override protected void doGet(HttpServletRequest request,HttpServletResponse response) throws java.io.IOException,jakarta.servlet.ServletException {
                boolean create="create".equals(request.getParameter("kind"));request.setAttribute("formKind",create?"create":"self");
                request.setAttribute("profileCancelUrl","/return");request.setAttribute("profileDraftUrl","/draft");request.setAttribute("profileSubmitUrl","/save");
                request.setAttribute("profileSubmitValue",create?"CREATE":"SUBMITTED");request.setAttribute("profileSubmitLabel",create?"kyc.create":"kyc.submit");
                request.getRequestDispatcher("/actions.jsp").forward(request,response);
            }
        });context.addServletMappingDecoded("/actions","actions");
        try {
            tomcat.start();var client=HttpClient.newHttpClient();
            for(String language:new String[]{"en","sw"})for(String kind:new String[]{"create","self"}) {
                var result=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/actions?kind="+kind)).header("Accept-Language",language).timeout(Duration.ofSeconds(60)).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertThat(result.statusCode()).as(result.body()).isEqualTo(200);
                assertThat(result.body()).contains("name=\"_csrf\" value=\"test-token\"","data-profile-previous","data-profile-next","formaction=\"/save\"");
                if(kind.equals("create"))assertThat(result.body()).contains("value=\"CREATE\"").doesNotContain("value=\"DRAFT\"","data-profile-save-draft","formaction=\"/draft\"");
                else assertThat(result.body()).contains("value=\"SUBMITTED\"","value=\"DRAFT\"","formaction=\"/draft\"","formnovalidate");
            }
        }finally{tomcat.stop();tomcat.destroy();spring.close();}
    }
}
