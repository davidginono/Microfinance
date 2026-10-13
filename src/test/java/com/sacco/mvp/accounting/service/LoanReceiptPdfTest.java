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
