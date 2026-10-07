package com.sacco.mvp.accounting.service;

import org.apache.catalina.startup.Tomcat;
import org.apache.jasper.servlet.JasperInitializer;
import org.apache.jasper.servlet.JspServlet;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Path;
import java.time.Duration;
import static org.assertj.core.api.Assertions.assertThat;

/** Exercise Jasper and all shared JSP includes without booting the application or connecting to a database. */
class ChartOfAccountsJspCompilationTest {
    @TempDir Path temporary;
    @Test void bothOnboardingViewsCompileWithTheSharedShell() throws Exception {
        var tomcat=new Tomcat();tomcat.setBaseDir(temporary.toString());tomcat.setPort(0);
        var context=tomcat.addContext("",Path.of("src/main/webapp").toAbsolutePath().toString());
        context.setParentClassLoader(getClass().getClassLoader());
        context.addServletContainerInitializer(new JasperInitializer(),null);
        String[] pages={"accounts.jsp","account-form.jsp"};
        for(int n=0;n<pages.length;n++) {
            var servlet=Tomcat.addServlet(context,"coa-"+n,new JspServlet());
            servlet.addInitParameter("jspFile","/WEB-INF/jsp/accounting/"+pages[n]);
            servlet.addInitParameter("development","true");
            context.addServletMappingDecoded("/coa-"+n,"coa-"+n);
        }
        try {
            tomcat.getConnector();tomcat.start();
            var client=HttpClient.newBuilder().connectTimeout(Duration.ofSeconds(10)).build();
            for(int n=0;n<pages.length;n++) {
                var request=HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/coa-"+n+"?jsp_precompile=true")).timeout(Duration.ofSeconds(60)).GET().build();
                var response=client.send(request,HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as("Jasper compilation of %s: %s",pages[n],response.body()).isEqualTo(200);
            }
        } finally {tomcat.stop();tomcat.destroy();}
    }
}
