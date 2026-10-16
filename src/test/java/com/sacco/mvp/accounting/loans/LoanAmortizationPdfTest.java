package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.controller.LoanRecordingController;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.loans.service.*;
import com.sacco.mvp.accounting.reports.LoanAmortizationPdfDocument.*;
import com.sacco.mvp.accounting.reports.VoucherPdfDocument.Field;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanAmortizationPdfTest {
    @Test void exportAndPrintUseAmortizationStationeryWithPaymentsBeforeScheduleInBothLanguages()throws Exception {
        var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);
        var id=UUID.randomUUID();var principal=new BigDecimal("9300.00");var first=LocalDate.of(2026,11,16);
        var calc=LoanAmortizationCalculator.estimate(principal,24,Map.of("calculationVersion",LoanAmortizationCalculator.VERSION,"interestRate","0.045","interestMethod","REDUCING_BALANCE","repaymentFrequency","MONTHLY","tenureUnit","MONTHS","tenureValue",24));
        var schedule=calc.rows().stream().map(r->new ScheduleRow(r.number(),first.plusMonths(r.number()-1L),r.principal(),r.interest(),r.amount(),r.remainingPrincipal().add(r.principal()),r.remainingPrincipal())).toList();
        var loan=new LoanRow(id,"100101",UUID.randomUUID(),"Synthetic Client","CL10002","Individual business loan",first.minusMonths(1),principal,principal,"DISBURSED",principal,BigDecimal.ZERO,BigDecimal.ZERO,calc.totalInterest(),BigDecimal.ZERO,BigDecimal.ZERO);
        var detail=new Detail(loan,"Example Microfinance Limited","Main branch","Accountant",OffsetDateTime.parse("2026-10-16T10:30:00+03:00"),"Business stock","Verified business",BigDecimal.ZERO,BigDecimal.ZERO,BigDecimal.ZERO,"VERIFIED","","","Reference","Synthetic example",new BigDecimal("0.045"),"REDUCING_BALANCE","MONTHLY",24,first,calc.installment(),calc.totalInterest(),schedule);
        var row=schedule.getFirst();var posted=new Posting(UUID.randomUUID(),id,"100101",loan.client(),loan.clientNumber(),"PAYMENT",first,"RV-EXAMPLE",row.amount(),row.principal(),row.interest(),"111001 - Cash","111002 - Principal","210001 - Interest","Reference","Synthetic payment",UUID.randomUUID(),"Accountant",first.atStartOfDay().atOffset(ZoneOffset.UTC),UUID.randomUUID(),UUID.randomUUID(),null,null,row.endingBalance(),loan.product());
        var service=mock(LoanRecordingService.class);var actor=mock(AppUserPrincipal.class);
        when(service.exportLoan(actor,id)).thenReturn(new LoanRecordingService.LoanExport(detail,List.of(posted)));
        var controller=new LoanRecordingController(service,new LoanRecordingPdfService(new ReportExportLimiter(1)),null,messages,null);
        for(var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw")))for(String format:List.of("pdf","print")){
            var response=(ResponseEntity<?>)controller.loanExport(actor,id,new MockHttpServletRequest("GET","/finance/loan-records/"+id+"/"+format),locale,null);
            assertThat(response.getHeaders().getFirst("Content-Disposition")).startsWith(format.equals("print")?"inline;":"attachment;");
            assertThat(response.getHeaders().getFirst("Cache-Control")).isEqualTo("no-store");
            try(var doc=Loader.loadPDF((byte[])response.getBody())){
                String text=new PDFTextStripper().getText(doc);
                String paymentTitle=messages.getMessage("recording.amortization.payments",null,locale).toUpperCase(Locale.ROOT),scheduleTitle=messages.getMessage("recording.schedule",null,locale).toUpperCase(Locale.ROOT);
                assertThat(text).contains("RV-EXAMPLE","100101","CL10002","24","0.00").doesNotContain("null");
                assertThat(text.indexOf(paymentTitle)).isLessThan(text.indexOf(scheduleTitle));
                if(locale.equals(Locale.ENGLISH) && format.equals("pdf")){
                    Files.write(Path.of("target/loan-amortization-template.pdf"),(byte[])response.getBody());
                    var renderer=new PDFRenderer(doc);
                    javax.imageio.ImageIO.write(renderer.renderImageWithDPI(0,120),"png",Path.of("target/loan-amortization-template.png").toFile());
                    javax.imageio.ImageIO.write(renderer.renderImageWithDPI(doc.getNumberOfPages()-1,120),"png",Path.of("target/loan-amortization-template-last.png").toFile());
                }
            }
        }
    }
    @Test void boundedLargeExportRetainsLastPaymentLastInstalmentAndRepeatedHeadings()throws Exception {
        var payments=IntStream.rangeClosed(1,500).mapToObj(n->List.of("16-Oct-2026","PAY-"+n,"100.00","10.00","110.00","900.00","Posted")).toList();
        var schedule=IntStream.rangeClosed(1,600).mapToObj(n->List.of(Integer.toString(n),"Month "+n,"100.00","110.00","10.00","100.00","0.00")).toList();
        var source=new Source("Loan amortization schedule","100101",List.of(new Field("Lender","Synthetic institution")),List.of(new Field("Loan amount","1,000.00")),
            new Table("Payment transactions",List.of("Date","Reference","Principal","Interest","Amount","Balance","Status"),new float[]{.12f,.22f,.12f,.12f,.13f,.16f,.13f},new boolean[]{false,false,true,true,true,true,false},payments,"No payments"),
            new Table("Repayment schedule",List.of("No.","Due date","Beginning balance","Scheduled payment","Interest","Principal","Ending balance"),new float[]{.055f,.14f,.16f,.17f,.14f,.15f,.185f},new boolean[]{false,false,true,true,true,true,true},schedule,"Unavailable"),"","Page");
        var renderer=new LoanRecordingPdfService(new ReportExportLimiter(1));
        try(var doc=Loader.loadPDF(renderer.renderAmortization(source))){
            assertThat(doc.getNumberOfPages()).isGreaterThan(3);String text=new PDFTextStripper().getText(doc);
            assertThat(text).contains("PAY-500","Month 600");
            assertThat(text.split("PAYMENT TRANSACTIONS",-1).length).isGreaterThan(2);
            assertThat(text.split("REPAYMENT SCHEDULE",-1).length).isGreaterThan(2);
        }
    }
}
