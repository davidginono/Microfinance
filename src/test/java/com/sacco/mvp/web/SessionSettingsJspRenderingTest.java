package com.sacco.mvp.web;

import com.sacco.mvp.domain.PlatformSessionSettings;
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
import java.util.Map;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;

class SessionSettingsJspRenderingTest {
    @TempDir Path temporary;

    @Test void warningCheckboxRendersSavedAndRejectedDraftValues() throws Exception {
        String source = Files.readString(Path.of("src/main/webapp/WEB-INF/jsp/admin/platform-settings-edit.jsp"));
        int start = source.indexOf("<form action=\"/admin/platform-settings/session-timeout\"");
        Files.writeString(temporary.resolve("session.jsp"), """
            <%@ taglib prefix="c" uri="jakarta.tags.core" %>
            <%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
            <%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
            """ + source.substring(start, source.indexOf("</form>", start) + 7));
        var tomcat = new Tomcat(); tomcat.setBaseDir(temporary.resolve("tomcat").toString()); tomcat.setPort(0);
        var context = tomcat.addContext("", temporary.toString());
        context.setParentClassLoader(getClass().getClassLoader());
        context.addServletContainerInitializer(new JasperInitializer(), null);
        var spring = new StaticWebApplicationContext();
        context.addServletContainerInitializer((classes, servletContext) -> {
            spring.setServletContext(servletContext); spring.getStaticMessageSource().setUseCodeAsDefaultMessage(true); spring.refresh();
            servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, spring);
        }, null);
        Tomcat.addServlet(context, "jsp", new JspServlet()); context.addServletMappingDecoded("*.jsp", "jsp");
        Tomcat.addServlet(context, "settings", new HttpServlet() {
            @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException, jakarta.servlet.ServletException {
                request.setAttribute("platformSessionSettings", PlatformSessionSettings.builder().timeoutMinutes(30)
                    .warningEnabled(Boolean.parseBoolean(request.getParameter("saved"))).build());
                if (request.getParameter("draft") != null) request.setAttribute("platformSettingsDraft",
                    Map.of("timeoutMinutes", "45", "warningEnabled", request.getParameter("draft")));
                request.getRequestDispatcher("/session.jsp").forward(request, response);
            }
        }); context.addServletMappingDecoded("/settings", "settings");
        try {
            tomcat.getConnector(); tomcat.start();
            var client = HttpClient.newHttpClient();
            for (String query : new String[]{"saved=false", "saved=true", "saved=true&draft=", "saved=false&draft=true"}) {
                var response = client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/settings?" + query))
                    .timeout(Duration.ofSeconds(60)).GET().build(), HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
                var checkbox = Pattern.compile("<input[^>]*name=\"warningEnabled\"[^>]*>").matcher(response.body());
                assertThat(checkbox.find()).isTrue();
                boolean expected = query.equals("saved=true") || query.endsWith("draft=true");
                assertThat(Pattern.compile("\\schecked(?:\\s|=|>)").matcher(checkbox.group()).find()).as(query).isEqualTo(expected);
                assertThat(response.body()).contains("value=\"" + (query.contains("draft=") ? "45" : "30") + "\"");
            }
        } finally { tomcat.stop(); tomcat.destroy(); spring.close(); }
    }
}
