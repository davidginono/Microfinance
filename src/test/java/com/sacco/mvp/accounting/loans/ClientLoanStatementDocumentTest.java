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
    @Test void screenPrintAndPdfUseCompactFieldsAndPostedPrincipalMovements() throws Exception {
        var service=mock(LoanRecordingService.class);var clock=mock(ApplicationClock.class);
        var actor=mock(AppUserPrincipal.class);var client=UUID.randomUUID();var loan=UUID.randomUUID();
        var date=LocalDate.of(2026,9,17);when(clock.today()).thenReturn(date.plusDays(2));
        var row=new LoanRow(loan,"7526",client,"James Mafumba","0001","Business loan",date,
            money("1000"),money("1000"),"DISBURSED",money("1000"),money("20"),money("0"),money("100"),money("0"),money("0"));
        var terms=new StatementTerms(loan,money("0.24"),"WEEKLY",12,52,date.plusDays(7),money("25"),"Test institution","Branch 1");
        var movements=List.of(post(loan,"DISBURSEMENT",date,"D001","1000","0","1000"),
            post(loan,"PAYMENT",date.plusDays(1),"P001","100","10","900"),
            post(loan,"REVERSAL",date.plusDays(2),"R001","100","10","1000"));
        var source=new LoanRecordingService.StatementExport(new StatementClient("0001","JAMES Mafumba","CLIENT"),List.of(row),movements,List.of(terms));
        var second = new LoanRow(UUID.randomUUID(),"7527",client,"James Mafumba","0001","Education loan",date,
            money("2150.50"),money("2150.50"),"DISBURSED",money("2150.50"),money("4.12"),money("0"),money("300"),money("0"),money("0"));
        when(service.exportStatement(actor,client)).thenReturn(new BalanceExport(source.client(),List.of(
            new StatementBalance(row,true),new StatementBalance(second,true)),date.plusDays(2)));
        when(service.loanStatement(actor,client,loan,0,false)).thenReturn(new LoanRecordingService.LoanStatementView(source,movements.getFirst(),0,false));
        when(service.loanStatement(actor,client,loan,0,true)).thenReturn(new LoanRecordingService.LoanStatementView(source,movements.getFirst(),0,false));
        var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);
        var renderer=new LoanRecordingPdfService(new ReportExportLimiter(1));var controller=new LoanRecordingController(mock(com.sacco.mvp.grouplending.service.SimpleGroupLoanService.class),service,mock(com.sacco.mvp.accounting.loans.service.LoanGroupApprovalService.class),renderer,clock,messages,mock(com.sacco.mvp.service.SaccoLogoStorageService.class),org.mockito.Mockito.mock(com.sacco.mvp.accounting.loans.service.LoanRecordingEvidenceService.class));
        var screen=new ExtendedModelMap();
        assertThat(controller.loanStatement(actor,client,loan,0,Locale.ENGLISH,screen)).isEqualTo("accounting/loans/loan-statement");
        var document=(StatementDocument)screen.get("document");
        var section=document.sections().getFirst();
        assertThat(section.columns()).containsExactly("Date","Details","Debit (principal)","Credit (principal)","Interest","Principal balance");
        assertThat(document.client()).hasSize(3);
        assertThat(section.fields()).hasSize(14);
        assertThat(section.fields()).extracting(StatementField::label)
            .contains("Loan ID","Loan product","Interest rate","First / last payment","Outstanding principal (TZS)","Due interest (TZS)","Outstanding charges","Overdue amount (TZS)")
            .doesNotContain("Fund","Opening principal balance (TZS)","Future scheduled interest (TZS)","Supporting evidence","Application component");
        assertThat(section.rows().get(1)).containsExactly("18/09/2026","Repayment / Marejesho ya mkopo","","100","10","900");
        assertThat(section.rows().get(2)).containsExactly("19/09/2026","Repayment reversal / Marejesho ya mkopo","100","","-10","1,000");
        var model=new ExtendedModelMap();var request=new MockHttpServletRequest("GET","/finance/client-ledgers/"+client+"/print");
        var print=(ResponseEntity<?>)controller.statementExport(actor,client,request,Locale.ENGLISH,model);
        assertThat(print.getHeaders().getFirst("Content-Disposition")).startsWith("inline");
        try(var printed=Loader.loadPDF((byte[])print.getBody())){
            assertThat(new PDFTextStripper().getText(printed).replaceAll("\\s+"," ")).contains("James’s Loan Statements","7526","7527","1,000","2,150.5","4.12").doesNotContain("Repayment reversal","JAMES Mafumba","1,000.00");
        }
        request.setRequestURI("/finance/client-ledgers/"+client+"/pdf");
        var response=(ResponseEntity<?>)controller.statementExport(actor,client,request,Locale.ENGLISH,new ExtendedModelMap());
        java.nio.file.Files.write(java.nio.file.Path.of("target/loan-statement-reference.pdf"),(byte[])response.getBody());
        try(var pdf=Loader.loadPDF((byte[])response.getBody())){
            assertThat(pdf.getPage(0).getMediaBox().getWidth()).isGreaterThan(pdf.getPage(0).getMediaBox().getHeight());
            assertThat(new PDFTextStripper().getText(pdf).replaceAll("\\s+"," ")).contains("James’s Loan Statements","7526","7527","Business loan","Education loan","Outstanding principal (TZS)").doesNotContain("Repayment reversal","JAMES Mafumba");
        }
        request.setRequestURI("/finance/client-ledgers/"+client+"/loans/"+loan+"/statement/pdf");
        try(var pdf=Loader.loadPDF(controller.loanStatementPdf(actor,client,loan,request,Locale.ENGLISH).getBody())){
            assertThat(new PDFTextStripper().getText(pdf).replaceAll("\\s+"," ")).contains("JAMES Mafumba","Repayment reversal","900","-10").doesNotContain("7527","900.00","Document reference","Document number");
        }
        var fixedTerms=new StatementTerms(loan,null,"WEEKLY",12,52,date.plusDays(7),money("25"),"Test institution","Branch 1",
            "AMOUNT","MONTHS",12,null,"",money("60"));
        var noPostings=new LoanRecordingService.StatementExport(source.client(),source.loans(),List.of(),List.of(fixedTerms));
        when(service.loanStatement(actor,client,loan,0,false)).thenReturn(new LoanRecordingService.LoanStatementView(noPostings,null,0,false));
        for(var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw"))){
            var emptyScreen=new ExtendedModelMap();controller.loanStatement(actor,client,loan,0,locale,emptyScreen);
            var emptySection=((StatementDocument)emptyScreen.get("document")).sections().getFirst();
            assertThat(emptySection.rows()).isEmpty();
            assertThat(emptySection.fields()).filteredOn(f->f.label().equals(messages.getMessage("recording.statement.fixedInterest",null,locale)))
                .extracting(StatementField::value).containsExactly("60");
            for(var key:List.of("recording.statement.disbursed","recording.statement.outstanding","recording.statement.dueInterest","charges.outstanding","recording.statement.arrears")){
                assertThat(emptySection.fields()).filteredOn(f->f.label().equals(messages.getMessage(key,null,locale)))
                    .extracting(StatementField::value).containsExactly("—");
            }
        }
    }
    @Test void landscapeStatementRepeatsHeadersAndKeepsEveryTransactionAcrossPages() throws Exception {
        var rows=IntStream.rangeClosed(1,180).mapToObj(n->List.of("18/09/2026",
            "Repayment "+n+" / Marejesho ya mkopo — é "+"Long description ".repeat(20),"","100.00","10.00","900.00")).toList();
        var source=new StatementDocument("CLIENT LOAN STATEMENT",List.of(new StatementField("Client Number","0001")),
            List.of(new StatementSection(List.of(new StatementField("Loan ID","7526")),List.of("Date","Details","Debit","Credit","Interest","Principal balance"),rows)),"No transactions","Amounts in TZS.");
        var renderer=new LoanRecordingPdfService(new ReportExportLimiter(1));
        try(var pdf=Loader.loadPDF(renderer.renderStatement(source))){
            assertThat(pdf.getNumberOfPages()).isGreaterThan(2);
            String text=new PDFTextStripper().getText(pdf);assertThat(text).contains("Repayment 1 /","Repayment 180 /","Marejesho ya mkopo — é");
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
    @Test void localizedFieldsAndRightmostAmountsStayWithinPrintableMargins() throws Exception {
        var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);
        for(var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw"))){
            var columns=List.of("date","description","debit","credit","interest","balance").stream()
                .map(key->messages.getMessage("recording.statement."+key,null,locale)).toList();
            var rows=IntStream.rangeClosed(1,45).mapToObj(n->List.of("19/10/2026", "Repayment "+n+" / Posted repayment with a description that wraps across several lines.","9,999,999.99","9,999,999.99","999,999.99","9,999,999.99")).toList();
            var source=new StatementDocument(messages.getMessage("recording.statement",null,locale).toUpperCase(locale),
                List.of(new StatementField("Client Number","CL10001"),new StatementField("Name","Long client name for statement layout checks"),new StatementField("As of","19-OCT-2026")),
                List.of(new StatementSection(List.of(new StatementField("Institution / Branch","Long institution name / Branch office with a long name")),columns,rows)),"No transactions","Amounts in TZS.");
            try(var pdf=Loader.loadPDF(new LoanRecordingPdfService(new ReportExportLimiter(1)).renderStatement(source))){
                assertThat(pdf.getNumberOfPages()).isGreaterThan(1);
                float right=pdf.getPage(0).getMediaBox().getWidth()-36;
                new PDFTextStripper(){
                    @Override protected void processTextPosition(TextPosition text){
                        assertThat(text.getXDirAdj()).as("Text respects the printable left margin").isGreaterThanOrEqualTo(36f);
                        assertThat(text.getXDirAdj()+text.getWidthDirAdj()).as("Rightmost text respects the printable right margin").isLessThanOrEqualTo(right+.01f);
                        super.processTextPosition(text);
                    }
                }.getText(pdf);
                assertThat(new PDFTextStripper().getText(pdf)).contains("Repayment 45 /","9,999,999.99");
            }
        }
    }
    private static BigDecimal money(String value){return new BigDecimal(value);}
    private static Posting post(UUID loan,String kind,LocalDate date,String reference,String principal,String interest,String balance){
        return new Posting(UUID.randomUUID(),loan,"7526","James Mafumba","0001",kind,date,reference,money(principal).add(money(interest)),money(principal),money(interest),
            "120 - Bank","Loans","Interest","","Marejesho ya mkopo",UUID.randomUUID(),"Accountant",date.atStartOfDay().atOffset(ZoneOffset.UTC),UUID.randomUUID(),UUID.randomUUID(),null,null,money(balance),"Business loan");
    }
}
