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
import static com.sacco.mvp.accounting.reports.VoucherPdfRows.assertRow;

class LoanReceiptPdfTest {
    @Test void loanReceiptAndCorrectionShowEachAccountOnItsPostedSide()throws Exception{
        var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");
        var renderer=new LoanReceiptPdfService(new LoanRecordingPdfService(new ReportExportLimiter(1)),messages);
        for(boolean reversal:List.of(false,true))for(var principal:List.of(new BigDecimal("100.00"),BigDecimal.ZERO)){
            var row=new LoanReceiptRow(1,UUID.randomUUID(),UUID.randomUUID(),"LN-1","Synthetic repayment",principal.add(new BigDecimal("20.01")),principal,new BigDecimal("20.01"),new BigDecimal("900.00"),"111001 - QA Cash","111002 - QA Loan Principal","210001 - QA Interest","REF-1");
            var voucher=new Voucher(UUID.randomUUID(),"RV-ROWS",Type.RECEIPT,LocalDate.of(2026,10,16),"Synthetic client","REF-1","Synthetic demonstration only.","Reference",UUID.randomUUID(),row.amount(),1,null,UUID.randomUUID(),"Synthetic accountant",OffsetDateTime.parse("2026-10-16T10:30:00+03:00"),reversal?UUID.randomUUID():null,null,"Example Microfinance Limited","Main branch",List.of());
            try(var doc=Loader.loadPDF(renderer.render(new LoanReceipt(voucher,"CLIENT-001",List.of(row)),Locale.ENGLISH,null))){
                String amount=row.amount().toPlainString();
                assertRow(doc,"111001",1,reversal?"":amount,reversal?amount:"");
                if(principal.signum()>0)assertRow(doc,"111002",2,reversal?"100.00":"",reversal?"":"100.00");
                assertRow(doc,"210001",principal.signum()>0?3:2,reversal?"20.01":"",reversal?"":"20.01");
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
                assertThat(text).contains("LN-"+count,"BANK-REF/"+count,"CLIENT-001","111001","110401","210001","900.00","20.01").doesNotContain("APPROVALS","CHECKED BY","APPROVED BY");
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
}
