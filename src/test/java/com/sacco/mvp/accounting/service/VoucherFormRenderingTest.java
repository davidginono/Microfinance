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
                var form=new Form();form.setEffectiveDate(java.time.LocalDate.of(2026,11,18));
                if(request.getParameter("split")!=null){var debit=new EntryForm();debit.setSide("DEBIT");debit.setAmount(java.math.BigDecimal.ONE);var credit=new EntryForm();credit.setSide("CREDIT");credit.setAmount(java.math.BigDecimal.ONE);form.getRows().getFirst().setEntries(List.of(debit,credit));}
                request.setAttribute("voucherForm",form);request.setAttribute("voucherType",request.getParameter("type"));request.setAttribute("base","/finance/vouchers/test");
                if(request.getParameter("split")!=null){
                    var amount=java.math.BigDecimal.ONE;
                    var debit=new Account(UUID.randomUUID(),"111001","Test bank","BANK");var credit=new Account(UUID.randomUUID(),"411001","Test account","OTHER");
                    request.setAttribute("preview",new Preview(List.of(new Transaction(UUID.randomUUID(),1,null,null,"","TOTAL","Test transaction",amount,debit,credit)),amount));
                }
                request.setAttribute("selectedAccounts",Map.of());request.getRequestDispatcher("/form.jsp").forward(request,response);
            }
        });context.addServletMappingDecoded("/form","form");
        try {
            tomcat.start();var client=HttpClient.newHttpClient();
            for(String language:List.of("en","sw"))for(Type type:Type.values())for(String split:List.of("","&split=true")){
                var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/form?type="+type+split)).header("Accept-Language",language).timeout(Duration.ofSeconds(60)).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as(response.body()).isEqualTo(200);
                assertThat(response.body()).doesNotContain("<spring:message","&lt;spring:message");
                var locale=Locale.forLanguageTag(language);
                assertThat(response.body()).contains("data-voucher-balance-error",messages.getMessage("voucher.balanceError",null,locale),messages.getMessage("voucher.error.balance",null,locale));
                assertThat(response.body()).contains("data-debit-label=\""+messages.getMessage("accounting.debit",null,locale)+"\"","data-credit-label=\""+messages.getMessage("accounting.credit",null,locale)+"\"","data-remove-entry-label=\""+messages.getMessage("voucher.removeEntry",null,locale)+"\"");
                if(!split.isEmpty())assertThat(response.body()).contains("name=\"rows[0].entries[1].amount\"","data-split-totals","data-resource-status=\"SUCCESS\"",messages.getMessage("voucher.balanced",null,locale));
                else assertThat(response.body()).doesNotContain("voucher-preview-status");
                String output=System.getProperty("microfinance.voucher.render-output");
                if(output!=null){Files.createDirectories(Path.of(output));Files.writeString(Path.of(output,language+"-"+type+(split.isEmpty()?"-new":"-split")+".html"),response.body());}
            }
        }finally{tomcat.stop();tomcat.destroy();spring.close();}
    }
}
