package com.sacco.mvp.web;

import com.sacco.mvp.domain.UserClaim;
import com.sacco.mvp.service.AdminService;
import jakarta.servlet.http.HttpServlet;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.catalina.startup.Tomcat;
import org.apache.jasper.servlet.JasperInitializer;
import org.apache.jasper.servlet.JspServlet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.EnumSet;
import java.util.Set;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class AccessMatrixJspRenderingTest {
    @TempDir Path temporary;

    @Test
    void savedClaimsRenderCheckedWhileUnassignedClaimsStayUnchecked() throws Exception {
        String source=Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/user-edit.jsp"));
        int start=source.indexOf("<c:set var=\"matrixColumns\"");
        String matrix=source.substring(start,source.indexOf("</section>",start)+"</section>".length());
        Files.writeString(temporary.resolve("matrix.jsp"),"""
            <%@ taglib prefix="c" uri="jakarta.tags.core" %>
            <%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
            """+matrix);
        Set<UserClaim> assigned=EnumSet.of(UserClaim.MANAGER_QUEUE_VIEW,UserClaim.VOUCHER_RULE_CONFIGURE);
        var user=AdminService.UserAccessView.builder().claims(assigned).build();
        var tomcat=new Tomcat();tomcat.setBaseDir(temporary.resolve("tomcat").toString());tomcat.setPort(0);
        var context=tomcat.addContext("",temporary.toString());
        context.setParentClassLoader(getClass().getClassLoader());
        context.addServletContainerInitializer(new JasperInitializer(),null);
        var spring=new StaticWebApplicationContext();
        context.addServletContainerInitializer((classes,servletContext)->{
            spring.setServletContext(servletContext);spring.getStaticMessageSource().setUseCodeAsDefaultMessage(true);spring.refresh();
            servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE,spring);
        },null);
        Tomcat.addServlet(context,"jsp",new JspServlet());context.addServletMappingDecoded("*.jsp","jsp");
        Tomcat.addServlet(context,"matrix",new HttpServlet(){
            @Override protected void doGet(HttpServletRequest request,HttpServletResponse response) throws java.io.IOException,jakarta.servlet.ServletException {
                request.setAttribute("user",user);request.setAttribute("accessMatrixRows",UserClaim.matrixRows());
                request.getRequestDispatcher("/matrix.jsp").forward(request,response);
            }
        });context.addServletMappingDecoded("/matrix","matrix");
        try {
            tomcat.getConnector();tomcat.start();
            var response=HttpClient.newHttpClient().send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/matrix"))
                .timeout(Duration.ofSeconds(60)).GET().build(),HttpResponse.BodyHandlers.ofString());
            assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
            for(var claim:UserClaim.matrixRows().stream().flatMap(row->row.claimsByAction().values().stream()).toList()){
                var input=Pattern.compile("<input[^>]*data-access-claim=\""+claim.name()+"\"[^>]*>").matcher(response.body());
                assertThat(input.find()).as(claim.name()).isTrue();
                assertThat(Pattern.compile("\\schecked(?:\\s|=|>)").matcher(input.group()).find())
                    .as("Saved selection for %s",claim).isEqualTo(assigned.contains(claim));
            }
        } finally {tomcat.stop();tomcat.destroy();spring.close();}
    }
}
