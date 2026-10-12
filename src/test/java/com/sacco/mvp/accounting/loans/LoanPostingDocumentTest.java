package com.sacco.mvp.accounting.loans;

import com.sacco.mvp.accounting.loans.controller.LoanRecordingController;
import com.sacco.mvp.accounting.loans.dto.LoanRecordingDtos.*;
import com.sacco.mvp.accounting.loans.service.*;
import com.sacco.mvp.security.AppUserPrincipal;
import com.sacco.mvp.service.*;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.rendering.PDFRenderer;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.ResourceBundleMessageSource;
import org.springframework.http.ResponseEntity;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.ui.ExtendedModelMap;
import java.math.BigDecimal;
import java.nio.file.*;
import java.time.*;
import java.util.*;
import static org.assertj.core.api.Assertions.*;
import static org.mockito.Mockito.*;

class LoanPostingDocumentTest {
    @Test void receiptDisbursementAndReversalUseSameScopedPdfForPrintAndDownload()throws Exception {
        var service=mock(LoanRecordingService.class);var actor=mock(AppUserPrincipal.class);
        var messages=new ResourceBundleMessageSource();messages.setBasename("messages");messages.setDefaultEncoding("UTF-8");messages.setFallbackToSystemLocale(false);
        var controller=new LoanRecordingController(service,new LoanRecordingPdfService(new ReportExportLimiter(1)),mock(ApplicationClock.class),messages,mock(SaccoLogoStorageService.class));
        for(var kind:List.of("PAYMENT","DISBURSEMENT","REVERSAL"))for(var locale:List.of(Locale.ENGLISH,Locale.forLanguageTag("sw"))){
            UUID id=UUID.randomUUID(),loan=UUID.randomUUID();var principal=new BigDecimal("1000.25");var interest=new BigDecimal(kind.equals("DISBURSEMENT")?"0.00":"100.10");
            var posting=new Posting(id,loan,"100001","Synthetic client","C001",kind,LocalDate.of(2026,10,12),"REF-"+kind,principal.add(interest),principal,interest,
                "111001 - Branch bank","112001 - Loan principal control","113001 - Interest clearing","Evidence-1","Synthetic posting",UUID.randomUUID(),"Synthetic accountant",OffsetDateTime.parse("2026-10-12T12:30:00+03:00"),null,null,kind.equals("REVERSAL")?UUID.randomUUID():null,null,new BigDecimal("9000.00"),"Business loan");
            var detail=mock(Detail.class);when(detail.institution()).thenReturn("Example Microfinance Limited");when(detail.branch()).thenReturn("Main branch");when(service.posting(actor,id)).thenReturn(posting);when(service.detail(actor,loan)).thenReturn(detail);
            var request=new MockHttpServletRequest("GET","/finance/loan-postings/"+id+"/pdf");
            var download=(ResponseEntity<?>)controller.postingExport(actor,id,request,locale,new ExtendedModelMap());
            request.setRequestURI("/finance/loan-postings/"+id+"/print");var print=(ResponseEntity<?>)controller.postingExport(actor,id,request,locale,new ExtendedModelMap());
            assertThat(print.getHeaders().getContentType().toString()).isEqualTo("application/pdf");assertThat(print.getHeaders().getFirst("Content-Disposition")).startsWith("inline;");
            try(var doc=Loader.loadPDF((byte[])download.getBody());var printed=Loader.loadPDF((byte[])print.getBody())){
                assertThat(doc.getNumberOfPages()).isEqualTo(1);
                String text=new PDFTextStripper().getText(doc);assertThat(new PDFTextStripper().getText(printed)).isEqualTo(text);
                assertThat(text).contains("REF-"+kind,"100001","Evidence-1",messages.getMessage("recording.document."+kind,null,locale),"1,000.25").doesNotContain("APPROVALS","CHECKED BY","APPROVED BY");
                if(locale.equals(Locale.ENGLISH)){
                    Files.write(Path.of("target/loan-"+kind.toLowerCase()+"-format.pdf"),(byte[])download.getBody());
                    javax.imageio.ImageIO.write(new PDFRenderer(doc).renderImageWithDPI(0,120),"png",Path.of("target/loan-"+kind.toLowerCase()+"-format.png").toFile());
                }
            }
        }
        verify(service,times(12)).authorize(actor,"EXPORT");
    }
    @Test void foreignBranchFailsBeforeRenderingOrLoadingBranding(){
        var service=mock(LoanRecordingService.class);var pdf=mock(LoanRecordingPdfService.class);var logos=mock(SaccoLogoStorageService.class);var actor=mock(AppUserPrincipal.class);var id=UUID.randomUUID();
        when(service.posting(actor,id)).thenThrow(new org.springframework.security.access.AccessDeniedException("Outside branch"));
        var controller=new LoanRecordingController(service,pdf,mock(ApplicationClock.class),mock(org.springframework.context.MessageSource.class),logos);
        assertThatThrownBy(()->controller.postingExport(actor,id,new MockHttpServletRequest("GET","/finance/loan-postings/"+id+"/print"),Locale.ENGLISH,new ExtendedModelMap())).isInstanceOf(org.springframework.security.access.AccessDeniedException.class);
        verifyNoInteractions(pdf,logos);
    }
}
