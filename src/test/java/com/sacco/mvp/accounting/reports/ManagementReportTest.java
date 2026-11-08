package com.sacco.mvp.accounting.reports;

import com.sacco.mvp.accounting.management.dto.ManagementReports.*;
import com.sacco.mvp.accounting.management.dto.ManagementReports;
import com.sacco.mvp.accounting.management.service.*;
import com.sacco.mvp.service.ReportExportLimiter;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class ManagementReportTest {
    static final LocalDate DAY=LocalDate.of(2026,10,15);
    static final OffsetDateTime NOW=OffsetDateTime.parse("2026-10-15T18:00:00+03:00");
    @Test void budgetsRequireEveryMonthAndExactNonnegativeCents(){
        var f=budget();BudgetService.validate(f,DAY);
        f.getAmounts().set(11,null);assertThatThrownBy(()->BudgetService.validate(f,DAY)).hasMessage("management.error.months");
        f.getAmounts().set(11,new BigDecimal("0.001"));assertThatThrownBy(()->BudgetService.validate(f,DAY)).hasMessage("management.error.months");
        f.getAmounts().set(11,new BigDecimal("-0.01"));assertThatThrownBy(()->BudgetService.validate(f,DAY)).hasMessage("management.error.months");
        f.setAmounts(new ArrayList<>(Collections.nCopies(12,BigDecimal.ZERO)));assertThatThrownBy(()->BudgetService.validate(f,DAY)).hasMessage("management.error.months");
    }
    @Test void reportDatesAndCountsAreBoundedAndCannotClaimFutureKnowledge(){
        var p=parameters(0);ManagementReportService.validate(p,DAY,NOW);
        assertThatThrownBy(()->ManagementReportService.validate(new Parameters(DAY.minusDays(367),DAY,NOW,null,null,null,"",0),DAY,NOW)).hasMessage("management.error.parameters");
        assertThatThrownBy(()->ManagementReportService.validate(new Parameters(DAY,DAY,NOW.plusSeconds(1),null,null,null,"",0),DAY,NOW)).hasMessage("management.error.parameters");
        assertThatThrownBy(()->ManagementReportService.validate(parameters(-1),DAY,NOW)).hasMessage("management.error.parameters");
    }
    @Test void expectedDatesAllowForecastsWithoutFutureRecordedKnowledgeOrOtherFutureReports(){
        var future=new Parameters(DAY,DAY.plusDays(30),NOW,null,null,null,"",0);
        ManagementReportService.validate(future,DAY,NOW,Kind.EXPECTED_REPAYMENTS);
        for(var kind:List.of(Kind.DISBURSED_LOANS_AUDIT,Kind.LOAN_REPAYMENT_TRANSACTIONS))
            assertThatThrownBy(()->ManagementReportService.validate(future,DAY,NOW,kind)).hasMessage("management.error.parameters");
        assertThatThrownBy(()->ManagementReportService.validate(new Parameters(DAY,DAY.plusDays(30),NOW.plusSeconds(1),null,null,null,"",0),DAY,NOW,Kind.EXPECTED_REPAYMENTS)).hasMessage("management.error.parameters");
        assertThat(ManagementReports.totalFields(Kind.DISBURSED_LOANS_AUDIT)).doesNotContain("instalment");
        assertThat(ManagementReports.totalFields(Kind.EXPECTED_REPAYMENTS)).doesNotContain("principalBalance");
        assertThat(ManagementReports.totalFields(Kind.LOAN_REPAYMENT_TRANSACTIONS)).doesNotContain("openingPrincipal","closingPrincipal");
        var unknown=new LinkedHashMap<String,BigDecimal>();unknown.put("disbursed",null);
        var result=new Result(Kind.DISBURSED_LOANS_AUDIT,parameters(0),"Institution","Branch",ManagementReports.columns(Kind.DISBURSED_LOANS_AUDIT),List.of(),List.of(new Group("2026-10 | Business loan",1,unknown)),unknown,1,1,false,"","");
        assertThat(result.getMonthlyTotals().get("2026-10")).containsEntry("disbursed",null);
    }
    @Test void selectorsKeepReportClaimsAndRejectOutsideOrReversedBounds(){
        var repository=org.mockito.Mockito.mock(ManagementReportRepository.class);
        var guard=org.mockito.Mockito.mock(com.sacco.mvp.accounting.service.GeneralLedgerService.class);
        var actor=org.mockito.Mockito.mock(com.sacco.mvp.security.AppUserPrincipal.class);
        var clock=org.mockito.Mockito.mock(com.sacco.mvp.service.ApplicationClock.class);
        org.mockito.Mockito.when(clock.today()).thenReturn(DAY);
        org.mockito.Mockito.when(clock.now()).thenReturn(NOW);
        var service=new ManagementReportService(repository,guard,clock,new ReportExportLimiter(1));
        UUID start=UUID.randomUUID(),end=UUID.randomUUID();
        assertThatThrownBy(()->service.selectedRange(actor,Kind.CASHBOOK,start,null)).hasMessage("management.error.cashBankAccount");
        org.mockito.Mockito.verify(guard).requireActor(actor,"FINANCIAL_REPORTS_VIEW");
        org.mockito.Mockito.verify(guard).requireActor(actor,"ACCOUNTING_JOURNALS_VIEW");
        org.mockito.Mockito.verify(guard,org.mockito.Mockito.never()).requireActor(actor,"ACCOUNTING_ACCOUNTS_CREATE");
        org.mockito.Mockito.when(repository.accounts(actor,"",0,List.of(start,end))).thenReturn(List.of(
            new Choice(start,"111003","Cash"),new Choice(end,"111001","Bank")));
        var reversed=new Parameters(DAY,DAY,NOW,start,end,null,"",0);
        assertThatThrownBy(()->service.report(actor,Kind.CASHBOOK,reversed,false)).hasMessage("management.error.range");
        org.mockito.Mockito.verify(repository,org.mockito.Mockito.never()).read(actor,Kind.CASHBOOK,reversed,false);
        assertThatThrownBy(()->service.choices(actor,Kind.CASHBOOK,"clients","x".repeat(101),0)).hasMessage("management.error.parameters");
        assertThatThrownBy(()->service.selectedClient(actor,Kind.CASHBOOK,UUID.randomUUID())).hasMessage("management.error.client");
    }
    @Test void pdfAndPrintDefinitionsRetainAllReferenceColumnsAndBilingualTotals()throws Exception {
        var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");
        var exporter=new ManagementReportPdf(messages,new ReportExportLimiter(1));
        for(var kind:Kind.values())for(var language:List.of("en","sw")){
            var columns=ManagementReports.columns(kind);var rows=new ArrayList<Map<String,Object>>();var totals=new LinkedHashMap<String,BigDecimal>();
            for(int n=0;n<60;n++){
                var row=new LinkedHashMap<String,Object>();row.put("groupName",kind==Kind.DAILY_COLLECTIONS?"2026-10-15 | Loan repayments":"Business loans");
                for(var c:columns)if(c.money()){row.put(c.key(),new BigDecimal("99999999999999.99"));if(ManagementReports.totalFields(kind).contains(c.key()))totals.merge(c.key(),(BigDecimal)row.get(c.key()),BigDecimal::add);}else row.put(c.key(),c.key().equals("entryKind")?"PAYMENT":c.key().equals("name")?"Synthetic client with a long readable name — sample "+n:c.key().equals("particulars")?"Long collection description ".repeat(2):"REF-"+n);
                rows.add(row);
            }
            var group=new Group(rows.getFirst().get("groupName").toString(),rows.size(),totals);
            var result=new Result(kind,parameters(0),"Synthetic reference institution","Main branch",columns,rows,List.of(group),totals,60,0,false,"111000 Cash and bank","");
            byte[] bytes=exporter.build(result,Locale.forLanguageTag(language));
            try(var doc=Loader.loadPDF(bytes)){
                String text=new PDFTextStripper().getText(doc);assertThat(text).contains("REF-59","15/10/2026").doesNotContain("2026-10-15").contains("5,999,999,999,999,999.40");
                if(kind!=Kind.PORTFOLIO_RISK && kind!=Kind.PORTFOLIO_ANALYSIS)assertThat(text).contains("01/10/2026");
                assertThat(doc.getNumberOfPages()).isGreaterThan(1);
                for(var c:columns)assertThat(text.replaceAll("\\s","")).contains(exporter.message("field."+c.key(),Locale.forLanguageTag(language)).split(" ")[0]);
            }
            if(Boolean.getBoolean("microfinance.management.samples")){var folder=java.nio.file.Path.of("target/management-reference-previews");java.nio.file.Files.createDirectories(folder);java.nio.file.Files.write(folder.resolve(kind+"-"+language+".pdf"),bytes);}
        }
    }
    @Test void partialPageCannotBecomeACompletePdf(){var messages=new ResourceBundleMessageSource();var exporter=new ManagementReportPdf(messages,new ReportExportLimiter(1));var r=new Result(Kind.CASHBOOK,parameters(1),"Institution","Branch",ManagementReports.columns(Kind.CASHBOOK),List.of(),List.of(),Map.of(),100,0,true,"","");assertThatThrownBy(()->exporter.build(r,Locale.ENGLISH)).hasMessage("management.error.large");}
    static BudgetForm budget(){var f=new BudgetForm();f.setAccountId(UUID.randomUUID());f.setDate(DAY);f.setYear(2027);f.setAmounts(new ArrayList<>(Collections.nCopies(12,new BigDecimal("10.01"))));return f;}
    static Parameters parameters(int page){return new Parameters(DAY.withDayOfMonth(1),DAY,NOW,null,null,null,"",page);}
}
