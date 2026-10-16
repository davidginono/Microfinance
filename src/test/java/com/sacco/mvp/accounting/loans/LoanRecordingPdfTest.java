package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService;
import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService.Document;
import com.sacco.mvp.service.ReportExportLimiter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.junit.jupiter.api.Test;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static com.sacco.mvp.accounting.reports.VoucherPdfRows.assertRow;
import static org.mockito.Mockito.*;
import com.sacco.mvp.accounting.loans.controller.LoanRecordingController;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.SaccoLogoStorageService;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.http.ResponseEntity;
import org.apache.pdfbox.rendering.PDFRenderer;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;

class LoanRecordingPdfTest {
    @Test void loanPaymentAndReceiptExportsSeparateAccountsAndPreserveCorrectionSides()throws Exception {
        var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");
        var service=mock(LoanRecordingService.class);var logos=mock(SaccoLogoStorageService.class);var actor=mock(AppUserPrincipal.class);
        when(actor.getSaccoId()).thenReturn("QA-INSTITUTION");
        var renderer=new LoanRecordingPdfService(new ReportExportLimiter(1));
        var controller=new LoanRecordingController(service,renderer,null,messages,logos);
        var detail=mock(Detail.class);when(detail.institution()).thenReturn("Example Microfinance Limited");when(detail.branch()).thenReturn("Main branch");
        for(String kind:List.of("DISBURSEMENT","PAYMENT","REVERSAL")){
            var id=UUID.randomUUID();var loan=UUID.randomUUID();boolean payment=kind.equals("DISBURSEMENT"),reversal=kind.equals("REVERSAL");
            var principal=new BigDecimal(payment?"1000.00":"100.00");var interest=new BigDecimal(payment?"0.00":"20.01");
            var posting=new Posting(id,loan,"LN-1","Synthetic client","CL-1",kind,LocalDate.of(2026,10,16),"QA-"+kind,principal.add(interest),principal,interest,"111001 - QA Cash","111002 - QA Loan Principal",payment?null:"210001 - QA Interest","Reference","Synthetic demonstration only.",UUID.randomUUID(),"Synthetic accountant",OffsetDateTime.parse("2026-10-16T10:30:00+03:00"),UUID.randomUUID(),UUID.randomUUID(),reversal?UUID.randomUUID():null,null,new BigDecimal("900.00"),"Product");
            when(service.posting(actor,id)).thenReturn(posting);when(service.detail(actor,loan)).thenReturn(detail);
            for(String format:List.of("pdf","print")){
                var response=(ResponseEntity<?>)controller.postingExport(actor,id,new MockHttpServletRequest("GET","/finance/loan-postings/"+id+"/"+format),Locale.ENGLISH,null);
                try(var pdf=Loader.loadPDF((byte[])response.getBody())){
                    boolean moneyDebit=!payment && !reversal;
                    assertRow(pdf,"111001",1,moneyDebit?"120.01":"",moneyDebit?"":payment?"1,000.00":"120.01",payment);
                    assertRow(pdf,"111002",2,moneyDebit?"":payment?"1,000.00":"100.00",moneyDebit?"100.00":"",payment);
                    if(!payment)assertRow(pdf,"210001",3,reversal?"20.01":"",reversal?"":"20.01");
                    assertThat(pdf.getNumberOfPages()).isEqualTo(1);
                    if(format.equals("pdf")){
                        Files.write(Path.of("target/loan-"+kind.toLowerCase()+"-rows.pdf"),(byte[])response.getBody());
                        javax.imageio.ImageIO.write(new PDFRenderer(pdf).renderImageWithDPI(0,120),"png",Path.of("target/loan-"+kind.toLowerCase()+"-rows.png").toFile());
                    }
                }
            }
        }
    }
    @Test void rendersCompletePaginatedDocumentWithEmbeddedMultilingualFont() throws Exception {
        var renderer = new LoanRecordingPdfService(new ReportExportLimiter(1));
        var lines = IntStream.rangeClosed(1,150)
            .mapToObj(n -> "Malipo / payment " + n + " | TZS 1,000.01 | Rejea ya malipo — taarifa ya mteja é").toList();
        byte[] result = renderer.render(new Document("Taarifa ya mkopo", "Taasisi ya majaribio", "Tawi la kwanza", "statement.pdf", lines));
        try (var pdf = Loader.loadPDF(result)) {
            assertThat(pdf.getNumberOfPages()).isGreaterThan(2);
            assertThat(new PDFTextStripper().getText(pdf)).contains("payment 1", "payment 150", "Taasisi ya majaribio", "taarifa ya mteja é");
            for (var page : pdf.getPages()) assertThat(page.getResources().getFontNames()).isNotEmpty();
        }
    }
}
