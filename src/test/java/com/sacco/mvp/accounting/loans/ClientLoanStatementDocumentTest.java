package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.controller.LoanRecordingController;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.loans.service.LoanRecordingService;
import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService;
import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.ApplicationClock;
import com.sacco.mvp.service.ReportExportLimiter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import java.math.BigDecimal;
import java.time.*;
import java.util.*;
import java.util.stream.IntStream;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

class ClientLoanStatementDocumentTest {
    @Test void htmlAndPdfShareStoredTermsAndPostedPrincipalMovements() throws Exception {
        var service=mock(LoanRecordingService.class);var clock=mock(ApplicationClock.class);
        var actor=mock(AppUserPrincipal.class);var client=UUID.randomUUID();var loan=UUID.randomUUID();
        var date=LocalDate.of(2026,9,17);when(clock.today()).thenReturn(date.plusDays(2));
        var row=new LoanRow(loan,"7526",client,"James Mafumba","0001","Business loan",date,
            money("1000"),money("1000"),"DISBURSED",money("1000"),money("20"),money("0"),money("100"),money("0"),money("0"));
        var terms=new StatementTerms(loan,money("0.24"),"WEEKLY",12,52,date.plusDays(7),money("25"),"Test institution","Branch 1");
        var movements=List.of(post(loan,"DISBURSEMENT",date,"D001","1000","0","1000"),
            post(loan,"PAYMENT",date.plusDays(1),"P001","100","10","900"),
            post(loan,"REVERSAL",date.plusDays(2),"R001","100","10","1000"));
        when(service.exportStatement(actor,client)).thenReturn(new LoanRecordingService.StatementExport("0001 - James Mafumba",List.of(row),movements,List.of(terms)));
        var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);
        var renderer=new LoanRecordingPdfService(new ReportExportLimiter(1));var controller=new LoanRecordingController(service,renderer,clock,messages,mock(com.sacco.mvp.service.SaccoLogoStorageService.class));
        var model=new ExtendedModelMap();var request=new MockHttpServletRequest("GET","/finance/client-ledgers/"+client+"/print");
        var print=(ResponseEntity<?>)controller.statementExport(actor,client,request,Locale.ENGLISH,model);
        assertThat(print.getHeaders().getFirst("Content-Disposition")).startsWith("inline");
        try(var printed=Loader.loadPDF((byte[])print.getBody())){
            assertThat(new PDFTextStripper().getText(printed).replaceAll("\\s+"," ")).contains("16-SEP-2027","1,000.00","24.00","900.00","-10.00");
        }
        request.setRequestURI("/finance/client-ledgers/"+client+"/pdf");
        var response=(ResponseEntity<?>)controller.statementExport(actor,client,request,Locale.ENGLISH,new ExtendedModelMap());
        java.nio.file.Files.write(java.nio.file.Path.of("target/loan-statement-reference.pdf"),(byte[])response.getBody());
        try(var pdf=Loader.loadPDF((byte[])response.getBody())){
            assertThat(pdf.getPage(0).getMediaBox().getWidth()).isGreaterThan(pdf.getPage(0).getMediaBox().getHeight());
            assertThat(new PDFTextStripper().getText(pdf).replaceAll("\\s+"," ")).contains("CLIENT LOAN STATEMENT","James Mafumba","D001","P001","R001","Principal balance","24.00");
        }
    }
    @Test void landscapeStatementRepeatsHeadersAndKeepsEveryTransactionAcrossPages() throws Exception {
        var rows=IntStream.rangeClosed(1,180).mapToObj(n->List.of("18-SEP-2026","REF-"+n,"Unavailable","Repayment","120",
            "Marejesho ya mkopo — é "+"Long description ".repeat(20),"","100.00","10.00","900.00")).toList();
        var source=new StatementDocument("CLIENT LOAN STATEMENT",List.of(new StatementField("Client Number","0001")),
            List.of(new StatementSection(List.of(new StatementField("Loan ID","7526")),List.of("Date","Reference","Document","Activity","Account","Description","Debit","Credit","Interest","Principal balance"),rows)),"No transactions","Amounts in TZS.");
        var renderer=new LoanRecordingPdfService(new ReportExportLimiter(1));
        try(var pdf=Loader.loadPDF(renderer.renderStatement(source))){
            assertThat(pdf.getNumberOfPages()).isGreaterThan(2);
            String text=new PDFTextStripper().getText(pdf);assertThat(text).contains("REF-1","REF-180","Marejesho ya mkopo — é");
            var stripper=new PDFTextStripper();for(int n=1;n<=pdf.getNumberOfPages();n++){stripper.setStartPage(n);stripper.setEndPage(n);assertThat(stripper.getText(pdf).replaceAll("\\s+"," ")).contains("CLIENT LOAN STATEMENT","Principal balance");}
            var bounds=new PDFTextStripper(){
                @Override protected void processTextPosition(TextPosition text){
                    assertThat(text.getXDirAdj()).as("Statement text stays inside the left page margin").isGreaterThanOrEqualTo(20);
                    assertThat(text.getXDirAdj()+text.getWidthDirAdj()).as("All columns, including principal balance, fit on the exported page").isLessThanOrEqualTo(822);
                    if(text.getFontSizeInPt()>=8)assertThat(text.getYDirAdj()).as("No transaction is clipped below the footer").isLessThan(555);
                    super.processTextPosition(text);
                }
            };
            bounds.getText(pdf);
        }
    }
    @Test void emptyStatementStillProducesAReadablePage() throws Exception {
        var source=new StatementDocument("TAARIFA YA MKOPO",List.of(new StatementField("Namba ya mteja","0001")),List.of(),"Hakuna miamala iliyowekwa.","Kiasi ni TZS.");
        try(var pdf=Loader.loadPDF(new LoanRecordingPdfService(new ReportExportLimiter(1)).renderStatement(source))){
            assertThat(pdf.getNumberOfPages()).isEqualTo(1);assertThat(new PDFTextStripper().getText(pdf)).contains("0001","Hakuna miamala iliyowekwa.");
        }
    }
    private static BigDecimal money(String value){return new BigDecimal(value);}
    private static Posting post(UUID loan,String kind,LocalDate date,String reference,String principal,String interest,String balance){
        return new Posting(UUID.randomUUID(),loan,"7526","James Mafumba","0001",kind,date,reference,money(principal).add(money(interest)),money(principal),money(interest),
            "120 - Bank","Loans","Interest","","Marejesho ya mkopo",UUID.randomUUID(),"Accountant",date.atStartOfDay().atOffset(ZoneOffset.UTC),UUID.randomUUID(),UUID.randomUUID(),null,null,money(balance),"Business loan");
    }
}
