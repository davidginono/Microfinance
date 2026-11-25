package com.sacco.mvp.web;

import com.sacco.mvp.accounting.approval.controller.VoucherWorkflowController;
import com.sacco.mvp.accounting.approval.dto.VoucherWorkflowDtos.*;
import com.sacco.mvp.accounting.approval.service.VoucherWorkflowService;
import com.sacco.mvp.accounting.dto.VoucherDtos.Type;
import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.security.AuthzService;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.web.view.CurrentUserView;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.*;
import org.apache.catalina.startup.Tomcat;
import org.apache.jasper.servlet.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.expression.DefaultHttpSecurityExpressionHandler;
import org.springframework.ui.ExtendedModelMap;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;

import java.io.IOException;
import java.math.BigDecimal;
import java.net.URI;
import java.net.http.*;
import java.nio.file.Path;
import java.time.Duration;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class VoucherApprovalRenderingTest {
    @TempDir Path temporary;

    @Test void scopedStaffCanReadRulesAndReviewCurrentAndHistoricalGroupsInBothLanguages() throws Exception {
        var admin = actor(Position.ICT_MANAGER, UserClaim.VOUCHER_RULE_VIEW);
        var reviewer = actor(Position.FINANCE_MANAGER, UserClaim.VOUCHER_WORKSPACE_VIEW,
            UserClaim.VOUCHER_APPROVAL_APPROVE, UserClaim.ACCOUNTING_JOURNALS_VIEW);
        var stages = List.of(new Stage("VOUCHER_APPROVAL_APPROVE", null, Position.FINANCE_MANAGER, 1),
            new Stage("VOUCHER_APPROVAL_APPROVE", null, Position.CHAIRPERSON, 1));
        var voucher = new Workflow(UUID.randomUUID(), "JV-TEST", Type.JOURNAL, "SINGLE", "", admin.getMemberId(),
            1, Approval.INITIATED, "VALIDATED", "NOT_POSTED", BigDecimal.TEN, BigDecimal.TEN,
            1, 0, 1, 1, stages, false, "", null, null, false, 2, "Rule 1");
        var workflows = mock(VoucherWorkflowService.class);
        when(workflows.configuration(admin, Type.JOURNAL)).thenReturn(new Configuration(Type.JOURNAL, 0, false, false, false, List.of()));
        when(workflows.register(reviewer, null, "", "", "updated_desc", 0, 0, 30))
            .thenReturn(new Page(List.of(voucher), 0, 30, false, Map.of()));
        when(workflows.reviewActions(reviewer, List.of(voucher))).thenReturn(Map.of(voucher.id(), Set.of("APPROVE")));
        when(workflows.details(reviewer, voucher.id())).thenReturn(new Details(voucher, List.of(), List.of(), Set.of("APPROVE"), Map.of()));
        var controller = new VoucherWorkflowController(workflows, null, null, null);

        var tomcat = new Tomcat();
        tomcat.setBaseDir(temporary.toString());
        tomcat.setPort(0);
        tomcat.getConnector().setProperty("address", "127.0.0.1");
        var context = tomcat.addContext("", Path.of("src/main/webapp").toAbsolutePath().toString());
        context.setParentClassLoader(getClass().getClassLoader());
        context.addServletContainerInitializer(new JasperInitializer(), null);
        var spring = new GenericWebApplicationContext();
        var messages = new ResourceBundleMessageSource();
        messages.setBasename("messages");
        messages.setDefaultEncoding("UTF-8");
        spring.getBeanFactory().registerSingleton("messageSource", messages);
        spring.getBeanFactory().registerSingleton("access", new AccessControlService());
        spring.getBeanFactory().registerSingleton("authz", mock(AuthzService.class));
        var expressions = new DefaultHttpSecurityExpressionHandler();
        expressions.setApplicationContext(spring);
        spring.getBeanFactory().registerSingleton("webSecurityExpressionHandler", new StaffNavigationRenderingTest.JspExpressions(expressions));
        context.addServletContainerInitializer((unused, servletContext) -> {
            spring.setServletContext(servletContext);
            spring.refresh();
            servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE, spring);
        }, null);
        Tomcat.addServlet(context, "jsp", new JspServlet());
        context.addServletMappingDecoded("*.jsp", "jsp");
        Tomcat.addServlet(context, "approval", new HttpServlet() {
            @Override protected void doGet(HttpServletRequest request, HttpServletResponse response) throws ServletException, IOException {
                String path = request.getRequestURI();
                boolean rules = path.endsWith("/configuration");
                var actor = rules ? admin : reviewer;
                SecurityContextHolder.getContext().setAuthentication(new UsernamePasswordAuthenticationToken(actor, null, actor.getAuthorities()));
                try {
                    var model = new ExtendedModelMap();
                    String view = rules ? controller.configuration(actor, Type.JOURNAL, model) :
                        path.endsWith("/approval") ? controller.list(actor, null, "", "", 0, 30, "updated_desc", 0, model) :
                        controller.details(actor, voucher.id(), model);
                    model.forEach(request::setAttribute);
                    request.setAttribute("currentMember", CurrentUserView.builder().fullName(actor.getFullName())
                        .position(actor.getPosition()).staffSession(true).build());
                    response.setLocale(request.getLocale());
                    request.getRequestDispatcher("/WEB-INF/jsp/" + view + ".jsp").forward(request, response);
                } finally {
                    SecurityContextHolder.clearContext();
                }
            }
        });
        context.addServletMappingDecoded("/finance/approval/*", "approval");
        try {
            tomcat.start();
            var client = HttpClient.newHttpClient();
            String base = "http://127.0.0.1:" + tomcat.getConnector().getLocalPort() + "/finance/approval";
            for (String language : List.of("en", "sw")) {
                var locale = Locale.forLanguageTag(language);
                String rules = get(client, base + "/configuration", language);
                for (var group : Position.staffAssignableRoles())
                    assertThat(rules).contains(messages.getMessage("userGroup." + group, null, locale));
                assertThat(rules).contains("<fieldset disabled>").doesNotContain("data-rule-add");
                String register = get(client, base, language);
                assertThat(register).contains("JV-TEST", "data-can-approve=\"true\"", "data-register-action=\"APPROVE\"", "value=\"JOURNAL\"")
                    .doesNotContain("value=\"PAYMENT\"", "value=\"RECEIPT\"", "data-register-action=\"REJECT\"");
                assertThat(get(client, base + "/" + voucher.id(), language)).contains("data-workflow-action=\"APPROVE\"",
                    messages.getMessage("userGroup.FINANCE_MANAGER", null, locale), messages.getMessage("admin.role.CHAIRPERSON", null, locale));
            }
        } finally {
            tomcat.stop();
            tomcat.destroy();
            spring.close();
        }
    }

    private static String get(HttpClient client, String url, String language) throws Exception {
        var response = client.send(HttpRequest.newBuilder(URI.create(url)).header("Accept-Language", language)
            .timeout(Duration.ofSeconds(90)).GET().build(), HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as("%s %s", url, response.body()).isEqualTo(200);
        return response.body();
    }

    private static AppUserPrincipal actor(Position group, UserClaim... claims) {
        var staff = Member.builder().id(UUID.randomUUID()).memberNo("TEST-" + group).fullName("Synthetic staff")
            .position(group).saccoId("I1").stationId("B1").staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build();
        return new AppUserPrincipal(staff, Set.of(claims));
    }
}
