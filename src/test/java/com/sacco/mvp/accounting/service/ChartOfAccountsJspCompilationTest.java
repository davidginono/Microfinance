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
    @Test void resourceViewsCompileWithTheSharedShell() throws Exception {
        var tomcat=new Tomcat();tomcat.setBaseDir(temporary.toString());tomcat.setPort(0);
        var context=tomcat.addContext("",Path.of("src/main/webapp").toAbsolutePath().toString());
        context.setParentClassLoader(getClass().getClassLoader());
        context.addServletContainerInitializer(new JasperInitializer(),null);
        String[] pages={"accounts.jsp","account-form.jsp","library.jsp","library-code-form.jsp","library-transaction.jsp","library-template-form.jsp","vouchers/register.jsp","vouchers/form.jsp","vouchers/details.jsp","loans/record-form.jsp","loans/register.jsp","loans/details.jsp","loans/posting.jsp","loans/statement.jsp","loans/statement-print.jsp","journal.jsp","business/openings/view.jsp","policies/view.jsp","reconciliation/review.jsp","reconciliation/statement.jsp","../reporting/loan-portfolio.jsp","../reports/accounting-release.jsp","../reports/statement-result.jsp","../reports/financial-statements.jsp","../reports/statement-mappings.jsp","../app/loan-new.jsp","../app/guarantor-selection.jsp","../admin/sms-usage.jsp","../admin/settings-controls.jsp","../admin/customised-loan-workflow.jsp"};
        if(Boolean.getBoolean("microfinance.receipt.jsp-only"))pages=new String[]{"vouchers/form.jsp","vouchers/loan-details.jsp"};
        else {
            var expanded=new java.util.LinkedHashSet<>(java.util.List.of(pages));
            expanded.add("vouchers/loan-details.jsp");
            // Compile every accountant resource view changed by the shared table rollout.
            String[] folders = {"accounting", "accountant", "reporting", "reports", "app", "admin", "manager", "board", "chairperson", "disbursement", "loan-officer", "staff", "repayments", "account"};
            if (Boolean.getBoolean("microfinance.workspace.jsp-only")) expanded.clear();
            for (String folder : folders) {
                try (var files=java.nio.file.Files.walk(Path.of("src/main/webapp/WEB-INF/jsp",folder))) {
                    for (var file:files.filter(path -> path.toString().endsWith(".jsp")).toList()) {
                        if (Boolean.getBoolean("microfinance.workspace.jsp-only") || java.nio.file.Files.readString(file).contains("data-resource-table="))
                            expanded.add("../"+Path.of("src/main/webapp/WEB-INF/jsp").relativize(file).toString().replace('\\','/'));
                    }
                }
            }
            pages=expanded.toArray(String[]::new);
        }
        String selectedPages=System.getProperty("microfinance.jsp.pages");
        if(selectedPages!=null)pages=selectedPages.split(",");
        for(int n=0;n<pages.length;n++) {
            var servlet=Tomcat.addServlet(context,"coa-"+n,new JspServlet());
            servlet.setLoadOnStartup(1);
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
