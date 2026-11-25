package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
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

class VoucherFormRenderingTest {
    @TempDir Path temporary;

    @Test void splitLabelsRenderAsTextInBothLanguagesForEveryVoucherType() throws Exception {
        var directory=Path.of("src/main/webapp/WEB-INF/jsp/accounting/vouchers");
        String source=Files.readString(directory.resolve("form.jsp"));
        String body=source.substring(source.indexOf("<c:url value=\"${base}\""),source.indexOf("</form:form>")+12);
        Files.copy(directory.resolve("shared-benefits.jspf"),temporary.resolve("shared-benefits.jspf"));
        Files.copy(directory.resolve("equity-fields.jspf"),temporary.resolve("equity-fields.jspf"));
        Files.copy(directory.resolve("activity-row.jspf"),temporary.resolve("activity-row.jspf"));
        Files.writeString(temporary.resolve("form.jsp"),"""
            <%@ page contentType="text/html; charset=UTF-8" pageEncoding="UTF-8" %>
            <%@ taglib prefix="c" uri="jakarta.tags.core" %>
            <%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
            <%@ taglib prefix="form" uri="http://www.springframework.org/tags/form" %>
            <%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
            <spring:htmlEscape defaultHtmlEscape="true" />
            """+body);
        var tomcat=new Tomcat();tomcat.setBaseDir(temporary.resolve("tomcat").toString());tomcat.setPort(0);
        tomcat.getConnector().setProperty("address","127.0.0.1");
        var context=tomcat.addContext("",temporary.toString());context.setParentClassLoader(getClass().getClassLoader());
        context.addServletContainerInitializer(new JasperInitializer(),null);
        var spring=new GenericWebApplicationContext();var messages=new ResourceBundleMessageSource();
        messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");spring.getBeanFactory().registerSingleton("messageSource",messages);
        context.addServletContainerInitializer((classes,servletContext)->{spring.setServletContext(servletContext);spring.refresh();servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE,spring);},null);
        Tomcat.addServlet(context,"jsp",new JspServlet());context.addServletMappingDecoded("*.jsp","jsp");
        Tomcat.addServlet(context,"form",new HttpServlet(){
            @Override protected void doGet(HttpServletRequest request,HttpServletResponse response)throws java.io.IOException,jakarta.servlet.ServletException {
                response.setLocale(request.getLocale());
                var form=new Form();form.setEffectiveDate(java.time.LocalDate.of(2026,11,18));
                if(request.getParameter("split")!=null){var debit=new EntryForm();debit.setSide("DEBIT");debit.setAmount(java.math.BigDecimal.ONE);var credit=new EntryForm();credit.setSide("CREDIT");credit.setAmount(java.math.BigDecimal.ONE);form.getRows().getFirst().setEntries(List.of(debit,credit));}
                if(request.getParameter("equity")!=null){form.setEquity(true);form.setEquityMovement(com.sacco.mvp.accounting.statements.dto.StatementSetupModels.Category.CONTRIBUTION);form.setEquityReason("Owner adds capital");}
                if("JOURNAL".equals(request.getParameter("type"))&&request.getParameter("equity")!=null){
                    var id=UUID.fromString("f82c2471-0267-4cc1-ba10-730c78ef5eb2");
                    var row=new com.sacco.mvp.accounting.statements.dto.StatementSetupModels.MovementRow(id,com.sacco.mvp.accounting.statements.dto.StatementSetupModels.Category.TRANSFER,"Profit <set aside>","Faida iliyotengwa");
                    var option=new com.sacco.mvp.accounting.statements.dto.StatementSetupModels.MovementOption("1:"+id,row);
                    form.setEquityMovement(null);form.setEquityChoice(option.value());
                    request.setAttribute("equityOptions",List.of(option));
                }
                request.setAttribute("canEquityVoucher",true);request.setAttribute("equityTypes",com.sacco.mvp.accounting.dto.VoucherDtos.equityTypes(Type.valueOf(request.getParameter("type"))));request.setAttribute("voucherForm",form);request.setAttribute("voucherType",request.getParameter("type"));request.setAttribute("base","/finance/vouchers/test");
                if(request.getParameter("split")!=null){
                    var amount=java.math.BigDecimal.ONE;
                    var debit=new Account(UUID.randomUUID(),"111001","Test bank","BANK");var credit=new Account(UUID.randomUUID(),"411001","Test account","OTHER");
                    request.setAttribute("preview",new Preview(List.of(new Transaction(UUID.randomUUID(),1,null,null,"","TOTAL","Test transaction",amount,debit,credit)),amount));
                }
                request.setAttribute("maxVoucherActivities",com.sacco.mvp.accounting.dto.VoucherDtos.MAX_ACTIVITIES);
                if(request.getParameter("activities")!=null){
                    var operations=new Activity(UUID.randomUUID(),"Operations <main>","Shughuli kuu");
                    var fees=new Activity(UUID.randomUUID(),"Fees","Ada");
                    form.setActivityIds(List.of(operations.id(),fees.id()));
                    var debit=new Account(UUID.randomUUID(),"111001","Test bank","BANK");
                    var credit=new Account(UUID.randomUUID(),"411001","Test account","OTHER");
                    var mapping=new Mapping(UUID.randomUUID(),UUID.randomUUID(),"Bank deposit","TOTAL",debit,credit,operations);
                    form.getRows().getFirst().setTransactionId(mapping.transactionId());
                    form.getRows().getFirst().setTemplateKey(mapping.templateKey());
                    request.setAttribute("voucherActivityIds",form.getActivityIds());
                    request.setAttribute("voucherActivities",Map.of(operations.id(),operations,fees.id(),fees));
                    request.setAttribute("voucherMappings",Map.of(0,mapping));
                }
                request.setAttribute("selectedAccounts",Map.of());request.getRequestDispatcher("/form.jsp").forward(request,response);
            }
        });context.addServletMappingDecoded("/form","form");
        try {
            tomcat.start();var client=HttpClient.newHttpClient();
            for(String language:List.of("en","sw"))for(Type type:Type.values())for(String split:List.of("","&split=true","&equity=true","&activities=true")){
                var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/form?type="+type+split)).header("Accept-Language",language).timeout(Duration.ofSeconds(60)).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
                assertThat(response.body()).doesNotContain("<spring:message","&lt;spring:message","name=\"reference\"");
                var locale=Locale.forLanguageTag(language);
                assertThat(response.body()).contains("data-voucher-balance-error",messages.getMessage("voucher.balanceError",null,locale),messages.getMessage("voucher.error.balance",null,locale));
                assertThat(response.body()).contains("data-debit-label=\""+messages.getMessage("accounting.debit",null,locale)+"\"","data-credit-label=\""+messages.getMessage("accounting.credit",null,locale)+"\"","data-remove-entry-label=\""+messages.getMessage("voucher.removeEntry",null,locale)+"\"");
                if(split.equals("&split=true"))assertThat(response.body()).contains("name=\"rows[0].entries[1].amount\"","data-split-totals","data-resource-status=\"SUCCESS\"",messages.getMessage("voucher.balanced",null,locale));
                else assertThat(response.body()).doesNotContain("voucher-preview-status");
                assertThat(response.body()).contains("value=\"EQUITY\"", "name=\"equity\"");
                if(split.equals("&equity=true")){
                    assertThat(response.body()).contains("Owner adds capital","name=\"equityChoice\"");
                    if(type==Type.JOURNAL)assertThat(response.body()).contains("value=\"1:f82c2471-0267-4cc1-ba10-730c78ef5eb2\" selected=\"selected\"",
                        language.equals("sw")?"Faida iliyotengwa":"Profit &lt;set aside&gt;");
                    else assertThat(response.body()).contains("value=\"CONTRIBUTION\" selected=\"selected\"").doesNotContain("value=\"EARNINGS_TRANSFER\"","value=\"TRANSFER\"");
                }
                if(split.equals("&activities=true")){
                    assertThat(response.body()).contains("name=\"activityIds\"", "data-voucher-add-activity", "Bank deposit",
                        messages.getMessage("voucher.addActivity",null,locale),messages.getMessage("voucher.removeActivity",null,locale));
                    assertThat(response.body()).contains(language.equals("sw")?"Shughuli kuu":"Operations &lt;main&gt;",
                        language.equals("sw")?"Ada":"Fees");
                }
                String output=System.getProperty("microfinance.voucher.render-output");
                if(output!=null){Files.createDirectories(Path.of(output));Files.writeString(Path.of(output,language+"-"+type+(split.equals("&activities=true")?"-activities":split.equals("&equity=true")?"-equity":split.isEmpty()?"-new":"-split")+".html"),response.body());}
            }
        }finally{tomcat.stop();tomcat.destroy();spring.close();}
    }
}
