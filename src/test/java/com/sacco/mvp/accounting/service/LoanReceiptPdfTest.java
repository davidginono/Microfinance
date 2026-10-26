package com.sacco.mvp.accounting.service;

import com.sacco.mvp.accounting.dto.VoucherDtos.*;
import com.sacco.mvp.accounting.loans.service.LoanRecordingPdfService;
import com.sacco.mvp.service.ReportExportLimiter;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;

class LoanReceiptPdfTest {
    @Test void groupReceiptsRetainPayerAndBeneficiaryAttributionInBothLanguages()throws Exception{
        var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);
        var renderer=new LoanReceiptPdfService(new LoanRecordingPdfService(new ReportExportLimiter(1)),messages);
        UUID first=UUID.randomUUID(),second=UUID.randomUUID();var attributions=new ArrayList<ReceiptContribution>();
        for(int n=1;n<=50;n++)attributions.add(new ReceiptContribution(UUID.randomUUID(),n,n%2==0?first:second,"LN-"+(n%2+1),UUID.randomUUID(),"PAYER-"+n,"Synthetic payer "+n,UUID.randomUUID(),"BORROWER-"+(n%2+1),"Synthetic borrower "+(n%2+1),"Contribution "+n,new BigDecimal("60.00"),new BigDecimal("50.00"),new BigDecimal("10.00"),BigDecimal.ZERO,null));
        var rows=List.of(new LoanReceiptRow(1,UUID.randomUUID(),first,"LN-1","Group collection",new BigDecimal("1500.00"),new BigDecimal("1250.00"),new BigDecimal("250.00"),new BigDecimal("1000.00"),"PRIVATE CASH ACCOUNT","PRIVATE PRINCIPAL ACCOUNT","PRIVATE INTEREST ACCOUNT","GROUP-REF/G1"),
            new LoanReceiptRow(2,UUID.randomUUID(),second,"LN-2","Group collection",new BigDecimal("1500.00"),new BigDecimal("1250.00"),new BigDecimal("250.00"),new BigDecimal("1000.00"),"PRIVATE CASH ACCOUNT","PRIVATE PRINCIPAL ACCOUNT","PRIVATE INTEREST ACCOUNT","GROUP-REF/G2"));
        var v=new Voucher(UUID.randomUUID(),"RV-GROUP-TEST",Type.RECEIPT,LocalDate.of(2026,10,25),"Synthetic treasurer","GROUP-REF","Synthetic group collection","Verified synthetic cash evidence",UUID.randomUUID(),new BigDecimal("3000.00"),2,null,UUID.randomUUID(),"Synthetic accountant",OffsetDateTime.parse("2026-10-25T10:30:00+03:00"),null,null,"Synthetic institution","Synthetic branch",List.of());
        var receipt=new LoanReceipt(v,"",rows,new GroupReceipt(UUID.randomUUID(),"GRP-TEST","Synthetic trading group",attributions,"RV-ORIGINAL-123",""));
        for(var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw")))try(var document=Loader.loadPDF(renderer.render(receipt,locale,null))){
            var text=new PDFTextStripper().getText(document);assertThat(text).contains("GRP-TEST","RV-ORIGINAL-123","Synthetic treasurer","PAYER-50","BORROWER-1","Contribution 50","60.00").doesNotContain("PRIVATE CASH ACCOUNT","PRIVATE PRINCIPAL ACCOUNT","PRIVATE INTEREST ACCOUNT",messages.getMessage("voucher.print.method",null,locale).toUpperCase(Locale.ROOT));
            if(locale.equals(Locale.ENGLISH)){javax.imageio.ImageIO.write(new PDFRenderer(document).renderImageWithDPI(0,100),"png",Path.of("target/group-receipt-first.png").toFile());javax.imageio.ImageIO.write(new PDFRenderer(document).renderImageWithDPI(document.getNumberOfPages()-1,100),"png",Path.of("target/group-receipt-last.png").toFile());}
        }
    }
    @Test void loanReceiptAndCorrectionShowOneTransactionWithoutLedgerAccounts()throws Exception{
        var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");
        var renderer=new LoanReceiptPdfService(new LoanRecordingPdfService(new ReportExportLimiter(1)),messages);
        for(boolean reversal:List.of(false,true))for(var principal:List.of(new BigDecimal("100.00"),BigDecimal.ZERO)){
            var row=new LoanReceiptRow(1,UUID.randomUUID(),UUID.randomUUID(),"LN-1","Synthetic repayment",principal.add(new BigDecimal("20.01")),principal,new BigDecimal("20.01"),new BigDecimal("900.00"),"111001 - QA Cash","111002 - QA Loan Principal","210001 - QA Interest","REF-1");
            var voucher=new Voucher(UUID.randomUUID(),"RV-ROWS",Type.RECEIPT,LocalDate.of(2026,10,16),"Synthetic client","REF-1","Synthetic demonstration only.","Reference",UUID.randomUUID(),row.amount(),1,null,UUID.randomUUID(),"Synthetic accountant",OffsetDateTime.parse("2026-10-16T10:30:00+03:00"),reversal?UUID.randomUUID():null,null,"Example Microfinance Limited","Main branch",List.of());
            try(var doc=Loader.loadPDF(renderer.render(new LoanReceipt(voucher,"CLIENT-001",List.of(row)),Locale.ENGLISH,null))){
                String text=new PDFTextStripper().getText(doc);
                assertThat(text).contains("Synthetic repayment","LN-1",row.amount().toPlainString()).doesNotContain("111001","111002","210001","QA Cash","QA Loan Principal","QA Interest","DEBIT","CREDIT","ACCOUNT / LEDGER");
                assertThat(text.split("Synthetic repayment",-1)).hasSize(2);
            }
        }
    }
    @Test void bilingualSingleAndFiftyLoanReceiptsRetainAllocationsAndLastRow()throws Exception{
        var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);
        var renderer=new LoanReceiptPdfService(new LoanRecordingPdfService(new ReportExportLimiter(1)),messages);
        for(int count:List.of(1,50))for(var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw"))){
            var rows=new ArrayList<LoanReceiptRow>();
            for(int n=1;n<=count;n++)rows.add(new LoanReceiptRow(n,UUID.randomUUID(),UUID.randomUUID(),"LN-"+n,"Synthetic collection "+n,new BigDecimal("120.01"),new BigDecimal("100.00"),new BigDecimal("20.01"),new BigDecimal("900.00"),"111001 - Branch cash","110401 - Loan principal","210001 - Interest clearing","BANK-REF/"+n));
            var v=new Voucher(UUID.randomUUID(),"RV-2026-SYNTHETIC",Type.RECEIPT,LocalDate.of(2026,10,13),"Synthetic client","BANK-REF","Synthetic receipt — demonstration only.","Payment reference",UUID.randomUUID(),new BigDecimal("120.01").multiply(BigDecimal.valueOf(count)),count,null,UUID.randomUUID(),"Synthetic accountant",OffsetDateTime.parse("2026-10-13T10:30:00+03:00"),null,null,"Example Microfinance Limited","Dar es Salaam — Main branch",List.of());
            byte[] bytes=renderer.render(new LoanReceipt(v,"CLIENT-001",rows),locale,null);
            try(var doc=Loader.loadPDF(bytes)){
                String text=new PDFTextStripper().getText(doc);
                assertThat(text).contains("LN-"+count,"BANK-REF/"+count,"CLIENT-001","120.01").doesNotContain("APPROVALS","CHECKED BY","APPROVED BY","111001","110401","210001",messages.getMessage("accounting.debit",null,locale).toUpperCase(locale),messages.getMessage("accounting.credit",null,locale).toUpperCase(locale));
                if(count==1)assertThat(doc.getNumberOfPages()).isEqualTo(1);
                if(count==50)assertThat(doc.getNumberOfPages()).isGreaterThan(2);
                if(locale.equals(Locale.ENGLISH)){
                    Files.write(Path.of("target/loan-receipt-"+count+".pdf"),bytes);
                    javax.imageio.ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0,100),"png",Path.of("target/loan-receipt-"+count+".png").toFile());
                    if(count==50)javax.imageio.ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(doc.getNumberOfPages()-1,100),"png",Path.of("target/loan-receipt-last.png").toFile());
                }
            }
        }
    }
    @Test void sharedReceiptRetainsAllNinetyNineBenefitEdgesAndSourceContributions()throws Exception{
        var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);
        var renderer=new LoanReceiptPdfService(new LoanRecordingPdfService(new ReportExportLimiter(1)),messages);var edges=new ArrayList<ReceiptContribution>();UUID loan=UUID.randomUUID();
        for(int i=1;i<=99;i++)edges.add(new ReceiptContribution(UUID.randomUUID(),i,loan,"SHARED-ONE",UUID.randomUUID(),"PAYER-"+((i+1)/2),"Synthetic contributor",UUID.randomUUID(),"BENEFIT-"+i,"Synthetic joint borrower","Synthetic verified cash",new BigDecimal("1.00"),new BigDecimal("0.60"),new BigDecimal("0.30"),new BigDecimal("0.10"),null,(i+1)/2));
        var row=new LoanReceiptRow(1,UUID.randomUUID(),loan,"SHARED-ONE","One parent payment",new BigDecimal("99.00"),new BigDecimal("59.40"),new BigDecimal("29.70"),new BigDecimal("100.00"),"PRIVATE CASH","PRIVATE PRINCIPAL","PRIVATE INTEREST","SHARED-REF/G1");
        var voucher=new Voucher(UUID.randomUUID(),"RV-SHARED-99",Type.RECEIPT,LocalDate.of(2026,10,25),"Synthetic treasurer","SHARED-REF","Synthetic shared receipt","Verified synthetic evidence",UUID.randomUUID(),new BigDecimal("99.00"),1,null,UUID.randomUUID(),"Synthetic accountant",OffsetDateTime.parse("2026-10-25T10:30:00+03:00"),null,null,"Synthetic institution","Synthetic branch",List.of());
        for(var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw")))try(var doc=Loader.loadPDF(renderer.render(new LoanReceipt(voucher,"",List.of(row),new GroupReceipt(UUID.randomUUID(),"SHARED-GROUP","Synthetic shared borrowers",edges)),locale,null))){
            var text=new PDFTextStripper().getText(doc);assertThat(text).contains("BENEFIT-99","PAYER-50","RV-SHARED-99","59.40","29.70","9.90","99.00",messages.getMessage("group.contribution",null,locale)+" 50").doesNotContain("PRIVATE CASH","PRIVATE PRINCIPAL","PRIVATE INTEREST");
            assertThat(doc.getNumberOfPages()).isGreaterThan(2);
            if(locale.equals(Locale.ENGLISH)){Files.write(Path.of("target/shared-receipt-99.pdf"),renderer.render(new LoanReceipt(voucher,"",List.of(row),new GroupReceipt(UUID.randomUUID(),"SHARED-GROUP","Synthetic shared borrowers",edges)),locale,null));javax.imageio.ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0,100),"png",Path.of("target/shared-receipt-99-first.png").toFile());javax.imageio.ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(doc.getNumberOfPages()-1,100),"png",Path.of("target/shared-receipt-99-last.png").toFile());}
        }
    }

}
