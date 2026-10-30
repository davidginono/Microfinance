package com.sacco.mvp.web;

import com.sacco.mvp.service.AdminService;
import jakarta.servlet.http.*;
import org.apache.catalina.startup.Tomcat;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.web.csrf.DefaultCsrfToken;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.StaticWebApplicationContext;
import java.nio.file.*;
import java.net.URI;
import java.net.http.*;
import java.time.Duration;
import java.util.UUID;
import static org.assertj.core.api.Assertions.*;

class StaffClientAccessRenderedTest {
    @TempDir Path temporary;

    @Test void actualJspFragmentRendersLocalizedScopedPostWithCsrfAndHidesIneligibleAction() throws Exception {
        var webroot = Files.createDirectory(temporary.resolve("webroot"));
        Files.copy(Path.of("src/main/webapp/WEB-INF/jsp/admin/staff-client-access.jspf"), webroot.resolve("staff-client-access.jspf"));
        Files.writeString(webroot.resolve("fixture.jsp"), """
            <%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
            <%@ taglib prefix="c" uri="jakarta.tags.core" %>
            <%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
            <!doctype html><html><head><meta name="viewport" content="width=device-width, initial-scale=1"></head>
            <body><main><%@ include file="staff-client-access.jspf" %></main></body></html>
            """);
        var tomcat = new Tomcat();
        tomcat.setBaseDir(temporary.resolve("tomcat").toString()); tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        var context = tomcat.addContext("", webroot.toString());
        context.setParentClassLoader(getClass().getClassLoader());
        Tomcat.initWebappDefaults(context);
        context.addServletContainerInitializer(new org.apache.jasper.servlet.JasperInitializer(), null);
        var spring = new StaticWebApplicationContext();
        spring.setServletContext(context.getServletContext());
        var messages = new ResourceBundleMessageSource(); messages.setBasename("messages"); messages.setDefaultEncoding("UTF-8");
        spring.getStaticMessageSource().setParentMessageSource(messages); spring.refresh();
        context.getServletContext().setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, spring);
        UUID account = UUID.randomUUID();
        Tomcat.addServlet(context, "fixture", new HttpServlet() {
            @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException, jakarta.servlet.ServletException {
                request.setAttribute("canEnableClientAccess", !"false".equals(request.getParameter("show")));
                request.setAttribute("user", AdminService.UserAccessView.builder().accountId(account).build());
                request.setAttribute("_csrf", new DefaultCsrfToken("X-CSRF-TOKEN", "_csrf", "synthetic-csrf"));
                request.getRequestDispatcher("/fixture.jsp").forward(request, response);
            }
        });
        context.addServletMappingDecoded("/fixture", "fixture");
        try {
            tomcat.start();
            String base = "http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/fixture";
            var client = HttpClient.newHttpClient();
            for (String language : new String[]{"en", "sw"}) {
                var response = client.send(HttpRequest.newBuilder(URI.create(base)).timeout(Duration.ofSeconds(60)).header("Accept-Language", language).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
                assertThat(response.body()).contains("/admin/users/" + account + "/enable-client-access", "method=\"post\"", "name=\"_csrf\" value=\"synthetic-csrf\"", language.equals("en") ? "Enable client access" : "Wezesha ufikiaji wa mteja");
                assertThat(response.body()).doesNotContain("<spring:message", "<c:if");
            }
            var hidden = client.send(HttpRequest.newBuilder(URI.create(base + "?show=false")).GET().build(), HttpResponse.BodyHandlers.ofString());
            assertThat(hidden.statusCode()).isEqualTo(200);
            assertThat(hidden.body()).doesNotContain("enable-client-access", "<form");
        } finally {tomcat.stop(); tomcat.destroy(); spring.close();}
    }
}
