package com.sacco.mvp.accounting.policy;

import com.sacco.mvp.service.AccessControlService;
import jakarta.servlet.http.*;
import org.apache.catalina.startup.Tomcat;
import org.apache.catalina.servlets.DefaultServlet;
import org.apache.catalina.webresources.DirResourceSet;
import org.apache.jasper.servlet.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import java.net.*;
import java.net.http.*;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Render the real JSPs with synthetic settings, without an application database. */
class AccountingPolicyJspRenderingTest {
    @TempDir Path temporary;
    @Test void settingsRenderInBothLanguagesWithSelectedValuesAndNoWrittenDecisionFields() throws Exception {
        var tomcat = new Tomcat(); tomcat.setBaseDir(temporary.toString()); tomcat.setPort(0);
        var context = tomcat.addContext("", Path.of("src/main/webapp").toAbsolutePath().toString());
        context.setParentClassLoader(getClass().getClassLoader());
        context.addServletContainerInitializer(new JasperInitializer(), null);
        var web = new GenericWebApplicationContext(); web.setServletContext(context.getServletContext());
        var messages = new ResourceBundleMessageSource(); messages.setBasename("messages"); messages.setDefaultEncoding("UTF-8"); messages.setFallbackToSystemLocale(false);
        web.getBeanFactory().registerSingleton("messageSource", messages);
        web.getBeanFactory().registerSingleton("access", new AccessControlService()); web.refresh();
        context.getServletContext().setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, web);
        var settings = AccountingPolicyServiceTest.command();
        settings.decisions().put(PolicyDecision.INTEREST_RECOGNITION, "ACCRUAL_DUE_INTEREST_V1");
        UUID account = UUID.randomUUID();
        var policy = new AccountingPolicyService.PolicySnapshot(UUID.randomUUID(), "I1", 2, settings.openingDate(), settings.effectiveFrom(),
            settings.authoritativeLedger(), settings.decisions(), settings.postingMatrix(), Map.of("BANK", account), "Synthetic document", UUID.randomUUID(),
            AccountingPolicyService.Decision.APPROVED, UUID.randomUUID(), "Synthetic review", "Synthetic reason", OffsetDateTime.now());
        var service = mock(AccountingPolicyService.class);
        when(service.settingsForm(null)).thenReturn(AccountingPolicyForm.from(policy));
        when(service.view(policy.id(), null)).thenReturn(policy);
        when(service.accountLabels(isNull(), anyCollection())).thenReturn(Map.of(account, "111001 - Example bank"));
        when(service.accountChoices(isNull(), anyString(), anyInt())).thenReturn(List.of(new AccountingPolicyService.AccountChoice(account, "111001 - Example bank")));
        var controller = new AccountingPolicyController(service);
        var previewDone = new java.util.concurrent.CountDownLatch(1);
        Tomcat.addServlet(context, "fixture", new HttpServlet() {
            @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws java.io.IOException, jakarta.servlet.ServletException {
                if ("true".equals(request.getParameter("finish"))) {response.getWriter().write("Preview complete"); previewDone.countDown(); return;}
                if (request.getServletPath().endsWith("/accounts")) {
                    response.setContentType("application/json");
                    response.getWriter().write(tools.jackson.databind.json.JsonMapper.builder().build().writeValueAsString(
                        controller.accounts(null, Objects.requireNonNullElse(request.getParameter("search"), ""), Integer.parseInt(Objects.requireNonNullElse(request.getParameter("page"), "0")))));
                    return;
                }
                var model = new ExtendedModelMap();
                String view = "view".equals(request.getParameter("page")) ? controller.view(policy.id(), null, model) : controller.createForm(null, model);
                model.forEach(request::setAttribute);
                response.setLocale(request.getLocale());
                request.getRequestDispatcher("/" + view).forward(request, response);
            }
        });
        context.addServletMappingDecoded("/preview", "fixture");
        context.addServletMappingDecoded("/finance/policies/accounts", "fixture");
        for (var page : List.of("new", "view")) {
            var jsp = Tomcat.addServlet(context, page, new JspServlet()); jsp.setLoadOnStartup(1);
            jsp.addInitParameter("jspFile", "/WEB-INF/jsp/accounting/policies/" + page + ".jsp");
            context.addServletMappingDecoded("/accounting/policies/" + page, page);
        }
        Tomcat.addServlet(context, "static", new DefaultServlet()); context.addServletMappingDecoded("/", "static");
        try {
            tomcat.getConnector(); tomcat.start();
            context.getResources().addPreResources(new DirResourceSet(context.getResources(), "/", Path.of("src/main/resources/static").toAbsolutePath().toString(), "/"));
            var client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/preview";
            for (String language : List.of("en", "sw")) for (String page : List.of("new", "view")) {
                var request = HttpRequest.newBuilder(URI.create(base + "?page=" + page)).header("Accept-Language", language).timeout(Duration.ofSeconds(45)).build();
                var response = client.send(request, HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
                assertThat(response.body()).contains("111001 - Example bank").doesNotContain("decisions[", "treatments[", "policy-explanation");
                if (page.equals("new")) assertThat(response.body()).contains("value=\"ACCRUAL_DUE_INTEREST_V1\" selected=\"selected\"");
            }
            if (Boolean.getBoolean("microfinance.policy.preview")) {
                System.out.println("SETTINGS_PREVIEW_URL=" + base);
                previewDone.await(5, java.util.concurrent.TimeUnit.MINUTES);
            }
        } finally {tomcat.stop(); tomcat.destroy(); web.close();}
    }
}
