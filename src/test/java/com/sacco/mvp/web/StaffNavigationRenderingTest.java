package com.sacco.mvp.web;

import com.sacco.mvp.domain.*;
import com.sacco.mvp.security.*;
import com.sacco.mvp.service.AccessControlService;
import com.sacco.mvp.staffoffice.model.StaffOffice;
import com.sacco.mvp.web.view.CurrentUserView;
import jakarta.servlet.http.*;
import org.apache.catalina.startup.Tomcat;
import org.apache.jasper.servlet.*;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.security.web.access.expression.DefaultHttpSecurityExpressionHandler;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import java.net.URI;
import java.net.http.*;
import java.nio.file.*;
import java.time.Duration;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.mock;

class StaffNavigationRenderingTest {
    @TempDir Path temporary;
    private static final String CLIENT="/finance/clients/onboarding",QUEUE="/disbursement/loan-applications",ARCHIVE="/disbursement/archive";

    @Test void assignedOfficesAndPermissionBasedActivitiesRenderFirstWithoutDuplicates() throws Exception {
        var fragments=Path.of("src/main/webapp/WEB-INF/jsp/fragments");
        try(var files=Files.list(fragments)){for(var file:files.filter(p->p.toString().endsWith(".jspf")).toList())Files.copy(file,temporary.resolve(file.getFileName()));}
        Files.writeString(temporary.resolve("navigation.jsp"),"<%@ page pageEncoding=\"UTF-8\" %><%@ include file=\"sidebar.jspf\" %>");
        var tomcat=new Tomcat();tomcat.setBaseDir(temporary.resolve("tomcat").toString());tomcat.setPort(0);tomcat.getConnector().setProperty("address","127.0.0.1");
        var context=tomcat.addContext("",temporary.toString());context.setParentClassLoader(getClass().getClassLoader());context.addServletContainerInitializer(new JasperInitializer(),null);
        var spring=new GenericWebApplicationContext();var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");
        spring.getBeanFactory().registerSingleton("messageSource",messages);spring.getBeanFactory().registerSingleton("access",new AccessControlService());
        spring.getBeanFactory().registerSingleton("authz",mock(AuthzService.class));
        var expressions=new DefaultHttpSecurityExpressionHandler();expressions.setApplicationContext(spring);
        spring.getBeanFactory().registerSingleton("webSecurityExpressionHandler",new JspExpressions(expressions));
        context.addServletContainerInitializer((classes,servletContext)->{
            servletContext.setSessionTrackingModes(Set.of(jakarta.servlet.SessionTrackingMode.COOKIE));
            spring.setServletContext(servletContext);spring.refresh();servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE,spring);
        },null);
        Tomcat.addServlet(context,"jsp",new JspServlet());context.addServletMappingDecoded("*.jsp","jsp");
        Tomcat.addServlet(context,"navigation",new HttpServlet(){
            @Override protected void doGet(HttpServletRequest request,HttpServletResponse response) throws java.io.IOException,jakarta.servlet.ServletException {
                var role=Position.valueOf(request.getParameter("role"));var claims=EnumSet.noneOf(UserClaim.class);
                for(var claim:request.getParameter("claims").split(","))if(!claim.isEmpty())claims.add(UserClaim.valueOf(claim));
                var actor=new AppUserPrincipal(Member.builder().id(UUID.randomUUID()).memberNo("SYNTHETIC").fullName("Synthetic staff").position(role)
                    .saccoId("I1").stationId("B1").staffAccessStatus(StaffAccessStatus.ACTIVE).status(MemberStatus.ACTIVE).build(),claims);
                var security=SecurityContextHolder.createEmptyContext();security.setAuthentication(new UsernamePasswordAuthenticationToken(actor,null,actor.getAuthorities()));
                SecurityContextHolder.setContext(security);
                try{
                    request.setAttribute("currentMember",CurrentUserView.builder().position(role).fullName(actor.getFullName()).staffSession(true).build());
                    request.setAttribute("staffOffices",StaffOffice.assigned(actor.getStaffRoles()));
                    request.setAttribute("isPlatformAdminIdentity",actor.isPlatformIdentity());
                    request.setAttribute("jakarta.servlet.forward.request_uri",Objects.requireNonNullElse(request.getParameter("path"),ARCHIVE));
                    request.setAttribute("pettyCashAvailable",Boolean.parseBoolean(request.getParameter("petty")));
                    request.getRequestDispatcher("/navigation.jsp").forward(request,response);
                }finally{SecurityContextHolder.clearContext();}
            }
        });context.addServletMappingDecoded("/navigation","navigation");
        try{
            tomcat.start();var client=HttpClient.newHttpClient();String base="http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/navigation";
            String all="CLIENT_ONBOARDING_VIEW,GROUP_LENDING_VIEW,DISBURSEMENT_QUEUE_VIEW,LOAN_RECORDING_VIEW";
            for(var role:Position.staffAssignableRoles()){
                if(role==Position.ADMIN)continue;
                String body=render(client,base,role,all,"en");
                var office=StaffOffice.assigned(Set.of(role)).getFirst();
                assertThat(body).doesNotContain("href=\"/staff/analytics\"","href=\""+office.getBase()+"/reports\"");
                String analytics=render(client,base,role,"STAFF_ANALYTICS_VIEW,LOAN_REPAYMENTS_VIEW","en",false,"/staff/analytics");
                assertThat(analytics).containsOnlyOnce("href=\"/staff/analytics\"")
                    .containsPattern("href=\"/staff/analytics\"[^>]*aria-current=\"page\"")
                    .doesNotContain("href=\"/repayments\"","href=\""+office.getBase()+"/reports\"");
                assertThat(body.indexOf(office.getBase()+"/queue")).isLessThan(body.indexOf("Office activities"));
                assertThat(body.indexOf("Office activities")).isLessThan(body.indexOf("href=\"/finance/loan-records\""));
                for(var path:List.of(CLIENT,"/finance/groups",QUEUE,ARCHIVE))assertThat(body).containsOnlyOnce("href=\""+path+"\"");
                assertThat(body).containsSubsequence("Office activities","href=\""+CLIENT+"\"","href=\"/finance/groups\"","href=\""+QUEUE+"\"");
                assertThat(body).doesNotContain(">Funding<");
                assertThat(body).containsPattern("href=\"/disbursement/archive\"[^>]*shell-nav-active[^>]*aria-current=\"page\"");
            }
            for(var role:List.of(Position.ACCOUNTANT,Position.CUSTOMER_CARE,Position.DISBURSEMENT_OFFICER)){
                String body=render(client,base,role,"","en");
                assertThat(body).doesNotContain("Office activities","href=\"/finance/groups\"","href=\""+CLIENT+"\"","href=\""+QUEUE+"\"","href=\""+ARCHIVE+"\"");
                body=render(client,base,role,"GROUP_LENDING_VIEW","en");
                assertThat(body).containsSubsequence("Office activities","href=\"/finance/groups\"").doesNotContain("href=\""+CLIENT+"\"","href=\""+QUEUE+"\"");
                body=render(client,base,role,"CLIENT_ONBOARDING_VIEW","sw");
                assertThat(body).contains("Shughuli za ofisi","href=\""+CLIENT+"\"").doesNotContain("href=\""+QUEUE+"\"","href=\""+ARCHIVE+"\"");
                for(String claim:List.of("DISBURSEMENT_QUEUE_VIEW","DISBURSEMENT_QUEUE_DISBURSE")){
                    body=render(client,base,role,claim,"en");
                    assertThat(body).contains("Office activities","href=\""+QUEUE+"\"","href=\""+ARCHIVE+"\"").doesNotContain("href=\""+CLIENT+"\"");
                }
            }
            assertThat(render(client,base,Position.ADMIN,all,"en")).doesNotContain("Office activities","href=\""+CLIENT+"\"","href=\""+QUEUE+"\"","href=\""+ARCHIVE+"\"");
            String ledger="/reports/financial/statements/ledger";
            String ledgerLink="href=\""+ledger+"\"";
            assertThat(render(client,base,Position.ACCOUNTANT,"ACCOUNTING_JOURNALS_VIEW","en")).doesNotContain(ledgerLink);
            assertThat(render(client,base,Position.ACCOUNTANT,"FINANCIAL_REPORTS_VIEW","en")).containsOnlyOnce(ledgerLink);
            for(String language:List.of("en","sw")){
                String reports=render(client,base,Position.ACCOUNTANT,"FINANCIAL_REPORTS_VIEW,ACCOUNTING_JOURNALS_VIEW,LOAN_REPORTS_VIEW",language,false,ledger);
                var links=java.util.regex.Pattern.compile("<a\\b[^>]*?\\shref=\"([^\"]+)\"").matcher(reports).results().map(match->match.group(1)).toList();
                assertThat(links).containsSequence("/reports/financial/operations/cashbook",ledger,"/reports/financial/operations/loan-portfolio");
                assertThat(reports).containsPattern(ledgerLink+"[^>]*shell-nav-active[^>]*>\\s*"+(language.equals("en")?"General ledger":"Leja kuu")+"\\s*</a>");
                String claims="ACCOUNTING_BUSINESS_VIEW,ACCOUNTING_ACCOUNTS_VIEW";
                assertThat(render(client,base,Position.ACCOUNTANT,claims,language)).contains("href=\"/finance/petty-cash/setup\"").doesNotContain("href=\"/finance/petty-cash\"");
                assertThat(render(client,base,Position.ACCOUNTANT,claims,language,true)).containsOnlyOnce("href=\"/finance/petty-cash\"");
                assertThat(render(client,base,Position.ACCOUNTANT,"ACCOUNTING_ACCOUNTS_VIEW",language,true)).doesNotContain("href=\"/finance/petty-cash\"");
            }
        }finally{tomcat.stop();tomcat.destroy();spring.close();}
    }
    private String render(HttpClient client,String base,Position role,String claims,String language) throws Exception {
        return render(client,base,role,claims,language,false);
    }
    private String render(HttpClient client,String base,Position role,String claims,String language,boolean configured) throws Exception {
        return render(client,base,role,claims,language,configured,ARCHIVE);
    }
    private String render(HttpClient client,String base,Position role,String claims,String language,boolean configured,String path) throws Exception {
        var response=client.send(HttpRequest.newBuilder(URI.create(base+"?role="+role+"&claims="+claims+"&petty="+configured+"&path="+path)).header("Accept-Language",language).timeout(Duration.ofSeconds(60)).GET().build(),HttpResponse.BodyHandlers.ofString());
        assertThat(response.statusCode()).as(response.body()).isEqualTo(200);return response.body();
    }
    // JSP tags use FilterInvocation; Spring Security 7's engine uses RequestAuthorizationContext.
    static class JspExpressions implements org.springframework.security.access.expression.SecurityExpressionHandler<org.springframework.security.web.FilterInvocation> {
        private final DefaultHttpSecurityExpressionHandler delegate;
        JspExpressions(DefaultHttpSecurityExpressionHandler delegate){this.delegate=delegate;}
        @Override public org.springframework.expression.ExpressionParser getExpressionParser(){return delegate.getExpressionParser();}
        @Override public org.springframework.expression.EvaluationContext createEvaluationContext(org.springframework.security.core.Authentication authentication,org.springframework.security.web.FilterInvocation invocation){
            return delegate.createEvaluationContext(()->authentication,new org.springframework.security.web.access.intercept.RequestAuthorizationContext(invocation.getRequest()));
        }
    }
}
