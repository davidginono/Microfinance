package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.accounting.management.dto.ManagementReports;
import com.sacco.mvp.accounting.management.dto.ManagementReports.*;
import com.sacco.mvp.accounting.management.service.*;
import com.sacco.mvp.service.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.poi.ss.usermodel.DataFormatter;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.apache.catalina.startup.Tomcat;
import org.apache.jasper.servlet.*;
import jakarta.servlet.http.*;
import org.springframework.web.context.WebApplicationContext;
import org.springframework.web.context.support.GenericWebApplicationContext;
import java.io.ByteArrayInputStream;
import java.nio.file.*;
import java.net.*;
import java.net.http.*;
import java.math.BigDecimal;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ReportTotalsTest {
    @TempDir Path temporary;
    Result grouped(Kind kind,Group... groups){
        return new Result(kind,ManagementReportTest.parameters(0),"Institution","Branch",ManagementReports.columns(kind),
            List.of(),List.of(groups),Map.of(),Arrays.stream(groups).mapToLong(Group::count).sum(),0,false,"","");
    }
    Group group(String name,long rows){return new Group(name,rows,Map.of("disbursed",BigDecimal.valueOf(rows)));}

    @Test void aSingleGroupNeverRepeatsTheGrandTotalAcrossAllReportKinds(){
        for(var kind:Kind.values())for(int count:List.of(1,20)){
            var result=grouped(kind,group("2026-11-01 | Product",count));
            assertThat(result.getSubtotalLayout().groups()).as(kind.name()).isEmpty();
            assertThat(result.getSubtotalLayout().periods()).as(kind.name()).isEmpty();
        }
    }
    @Test void productAndAccountSubtotalsOnlySummarizeMultipleRowsWithinALargerReport(){
        for(var kind:List.of(Kind.CASHBOOK,Kind.EXPECTED_REPAYMENTS,Kind.LOAN_REPAYMENT_TRANSACTIONS,Kind.LOAN_PORTFOLIO,Kind.PORTFOLIO_RISK,Kind.PORTFOLIO_ANALYSIS)){
            var result=grouped(kind,group("A",2),group("B",1),group("C",3));
            assertThat(result.getSubtotalLayout().groups()).containsExactlyInAnyOrder("A","C");
            assertThat(result.getSubtotalLayout().periods()).isEmpty();
        }
    }
    @Test void monthlyAndDailySubtotalsReplaceAnIdenticalSingleGroupSubtotal(){
        for(var kind:List.of(Kind.DISBURSED_LOANS_AUDIT,Kind.DAILY_COLLECTIONS)){
            String first=kind==Kind.DAILY_COLLECTIONS?"2026-10-01":"2026-10";
            String next=kind==Kind.DAILY_COLLECTIONS?"2026-10-02":"2026-11";
            var result=grouped(kind,group(first+" | A",2),group(next+" | B",1));
            assertThat(result.getSubtotalLayout().groups()).isEmpty();
            assertThat(result.getSubtotalLayout().periods()).containsExactly(first);
            result=grouped(kind,group(first+" | A",2),group(first+" | B",3),group(next+" | C",2));
            assertThat(result.getSubtotalLayout().groups()).containsExactlyInAnyOrder(first+" | A",first+" | B");
            assertThat(result.getSubtotalLayout().periods()).containsExactlyInAnyOrder(first,next);
        }
    }
    @Test void aSinglePeriodUsesTheGrandTotalWhileKeepingUsefulProductSubtotals(){
        var result=grouped(Kind.DISBURSED_LOANS_AUDIT,group("2026-11 | A",2),group("2026-11 | B",3));
        assertThat(result.getSubtotalLayout().groups()).containsExactlyInAnyOrder("2026-11 | A","2026-11 | B");
        assertThat(result.getSubtotalLayout().periods()).isEmpty();
    }
    @Test void instalmentSizesRemainDetailFieldsAndAreNeverSummed(){
        assertThat(ManagementReports.totalFields(Kind.DISBURSED_LOANS_AUDIT)).containsExactly("disbursed","totalInterest","netDisbursed");
        assertThat(ManagementReports.documentColumns(Kind.DISBURSED_LOANS_AUDIT)).extracting(Column::key).contains("principalInstallment","instalment");
        assertThat(ManagementReports.totalFields(Kind.LOAN_PORTFOLIO)).doesNotContain("instalment");
        assertThat(ManagementReports.totalFields(Kind.EXPECTED_REPAYMENTS)).doesNotContain("principalBalance");
        assertThat(ManagementReports.totalFields(Kind.LOAN_REPAYMENT_TRANSACTIONS)).doesNotContain("openingPrincipal","closingPrincipal");
    }
    @Test void pdfAndExcelUseOnlyOneOverallTotalForOneGroupInBothLanguages() throws Exception {
        var messages=LoanReportExcelTest.messages();var clock=new ApplicationClock("Africa/Dar_es_Salaam");
        var pdf=new ManagementReportPdf(messages,new ReportExportLimiter(1),clock);
        var excel=new ManagementReportExcel(messages,new ReportExportLimiter(1),clock);
        var fixture=new LoanReportExcelTest();
        for(var kind:Kind.values())for(var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw"))){
            var result=fixture.result(kind,1);
            String group=messages.getMessage("management.groupTotal",null,locale);
            String month=messages.getMessage("management.monthTotal",null,locale);
            String day=messages.getMessage("management.dayTotal",null,locale);
            String grand=messages.getMessage("management.total",null,locale);
            try(var document=Loader.loadPDF(pdf.build(result,locale))){
                String text=new PDFTextStripper().getText(document).replaceAll("\\s+"," ");
                assertThat(text).as(kind+" PDF "+locale).doesNotContain(group,month,day).containsOnlyOnce(grand);
            }
            if(ManagementReportExcel.supports(kind))try(var book=new XSSFWorkbook(new ByteArrayInputStream(excel.build(result,locale)))){
                var labels=new ArrayList<String>();var formatter=new DataFormatter();
                for(var row:book.getSheetAt(0))labels.add(formatter.formatCellValue(row.getCell(0)));
                assertThat(labels).as(kind+" Excel "+locale).doesNotContain(group,month,day);
                assertThat(Collections.frequency(labels,grand)).isEqualTo(1);
            }
        }
    }
    @Test void actualPreviewTemplatesRenderOneTotalAcrossAllEightReports() throws Exception {
        var source=Path.of("src/main/webapp/WEB-INF/jsp/accounting/management");
        for(String file:List.of("document.jspf","source-tables.jspf","source-total.jspf"))Files.copy(source.resolve(file),temporary.resolve(file));
        Files.createDirectories(temporary.resolve("WEB-INF"));
        Files.copy(Path.of("src/main/webapp/WEB-INF/application-time.tld"),temporary.resolve("WEB-INF/application-time.tld"));
        Files.writeString(temporary.resolve("report.jsp"),"""
            <%@ page pageEncoding="UTF-8" %>
            <%@ taglib prefix="c" uri="jakarta.tags.core" %>
            <%@ taglib prefix="fn" uri="jakarta.tags.functions" %>
            <%@ taglib prefix="fmt" uri="jakarta.tags.fmt" %>
            <%@ taglib prefix="spring" uri="http://www.springframework.org/tags" %>
            <%@ include file="document.jspf" %>
            """);
        var tomcat=new Tomcat();tomcat.setBaseDir(temporary.resolve("tomcat").toString());tomcat.setPort(0);
        tomcat.getConnector().setProperty("address","127.0.0.1");
        var context=tomcat.addContext("",temporary.toString());context.setParentClassLoader(getClass().getClassLoader());
        context.addServletContainerInitializer(new JasperInitializer(),null);
        var spring=new GenericWebApplicationContext();spring.getBeanFactory().registerSingleton("messageSource",LoanReportExcelTest.messages());
        context.addServletContainerInitializer((classes,servletContext)->{
            spring.setServletContext(servletContext);spring.refresh();
            servletContext.setAttribute(WebApplicationContext.ROOT_WEB_APPLICATION_CONTEXT_ATTRIBUTE,spring);
        },null);
        Tomcat.addServlet(context,"jsp",new JspServlet());context.addServletMappingDecoded("*.jsp","jsp");
        Tomcat.addServlet(context,"preview",new HttpServlet(){
            @Override protected void doGet(HttpServletRequest request,HttpServletResponse response) throws java.io.IOException,jakarta.servlet.ServletException {
                var kind=Kind.valueOf(request.getParameter("kind"));var result=new LoanReportExcelTest().result(kind,1);
                request.setAttribute("report",result);request.setAttribute("referenceLocale",request.getLocale());
                request.setAttribute("referenceStamp","Printed");request.setAttribute("referencePeriod","Period");request.setAttribute("referenceDetails",List.of());
                var headings=new HashMap<String,String>();result.getDocumentColumns().forEach(c->headings.put(c.key(),c.key()));
                request.setAttribute("referenceHeadings",headings);
                request.setAttribute("referenceWidths",Collections.nCopies(result.getDocumentColumns().size()+(kind==Kind.DISBURSED_LOANS_AUDIT?1:0),50));
                request.getRequestDispatcher("/report.jsp").forward(request,response);
            }
        });context.addServletMappingDecoded("/preview","preview");
        try{
            tomcat.start();var client=HttpClient.newHttpClient();
            for(var kind:Kind.values())for(String language:List.of("en","sw")){
                var response=client.send(HttpRequest.newBuilder(URI.create("http://127.0.0.1:"+tomcat.getConnector().getLocalPort()+"/preview?kind="+kind))
                    .header("Accept-Language",language).GET().build(),HttpResponse.BodyHandlers.ofString());
                assertThat(response.statusCode()).as(kind+" "+response.body()).isEqualTo(200);
                assertThat(response.body().split("<tr class=\"reference-total\"",-1)).as(kind+" HTML "+language).hasSize(2);
                assertThat(response.body()).contains(LoanReportExcelTest.messages().getMessage("management.total",null,Locale.forLanguageTag(language)));
            }
        }finally{tomcat.stop();tomcat.destroy();spring.close();}
    }
}
